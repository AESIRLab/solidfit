package com.example.solidfit.data

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.os.RemoteException
import android.util.Log
import com.zybooks.solidcredentialmanager.ICredentialManager
import com.zybooks.solidcredentialmanager.ITokenCallback
import com.zybooks.solidcredentialmanager.IWebIdListCallback
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

private const val TAG = "ScmClient"
private const val SCM_PACKAGE = "com.zybooks.solidcredentialmanager"
private const val SCM_SERVICE = "com.zybooks.solidcredentialmanager.services.CredentialManagerService"
private const val CONNECT_TIMEOUT_MS = 5_000L
private const val REQUEST_TIMEOUT_MS = 30_000L
private const val SUPPORTED_REPLY_VERSION = 1

/** The Solid Credential Manager's answer to a token request (contract version 1). */
sealed interface ScmReply {
    data class Granted(val webId: String, val accessToken: String, val expiresAt: Long, val dpopKey: String) : ScmReply
    data class ConsentRequired(val webId: String, val action: PendingIntent?) : ScmReply
    data class LoginRequired(val webId: String, val action: PendingIntent?) : ScmReply
    data object UnknownWebId : ScmReply
    data object Denied : ScmReply
    data object TransientError : ScmReply
    /** The SCM is not installed or could not be reached. */
    data object Unavailable : ScmReply

    companion object {
        /** Reads the reply JSON; anything unreadable or of an unknown version is a transient error. */
        fun parse(json: String?, action: PendingIntent?): ScmReply {
            val reply = try {
                JSONObject(json ?: return TransientError)
            } catch (e: Exception) {
                return TransientError
            }
            if (reply.optInt("v") != SUPPORTED_REPLY_VERSION) return TransientError
            val webId = reply.optString("webId")
            return when (reply.optString("status")) {
                "GRANTED" -> {
                    val accessToken = reply.optString("accessToken")
                    val dpopKey = reply.optString("dpopKey")
                    val expiresAt = reply.optLong("expiresAt")
                    if (webId.isBlank() || accessToken.isBlank() || dpopKey.isBlank() || expiresAt <= 0) {
                        TransientError
                    } else {
                        Granted(webId, accessToken, expiresAt, dpopKey)
                    }
                }
                "CONSENT_REQUIRED" -> ConsentRequired(webId, action)
                "LOGIN_REQUIRED" -> LoginRequired(webId, action)
                "UNKNOWN_WEBID" -> UnknownWebId
                "DENIED" -> Denied
                else -> TransientError
            }
        }
    }
}

/**
 * Ask the SCM for a new token when less than min(2 minutes, a sixth of the token's lifetime)
 * remains. That is always below the SCM's own refresh threshold (min(5 min, a third)), so the SCM
 * refreshes before answering and the two can't loop.
 */
fun needsScmToken(expiresAt: Long, receivedAt: Long, now: Long): Boolean {
    val lifetime = (expiresAt - receivedAt).coerceAtLeast(0L)
    val threshold = minOf(2 * 60_000L, lifetime / 6)
    return expiresAt - now < threshold
}

/** Collapses concurrent requests for the same WebID into one call to [send]. */
class SingleFlightRequests(private val send: suspend (webId: String) -> ScmReply) {
    private val lock = Mutex()
    private val inFlight = mutableMapOf<String, CompletableDeferred<ScmReply>>()

    suspend fun request(webId: String): ScmReply {
        var existing: CompletableDeferred<ScmReply>? = null
        val mine = CompletableDeferred<ScmReply>()
        lock.withLock {
            existing = inFlight[webId]
            if (existing == null) inFlight[webId] = mine
        }
        existing?.let { return it.await() }
        val result = try {
            send(webId)
        } catch (e: CancellationException) {
            lock.withLock { inFlight.remove(webId) }
            mine.cancel(e)
            throw e
        } catch (e: Exception) {
            ScmReply.TransientError
        }
        lock.withLock { inFlight.remove(webId) }
        mine.complete(result)
        return result
    }
}

/** One process-wide connection to the Solid Credential Manager. */
object ScmClient {

    private val connectLock = Mutex()
    @Volatile private var service: ICredentialManager? = null
    private val requests = SingleFlightRequests { webId -> sendTokenRequest(webId) }
    private lateinit var appContext: Context

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = ICredentialManager.Stub.asInterface(binder)
            pendingConnect?.complete(service)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
        }

        override fun onBindingDied(name: ComponentName) {
            service = null
            pendingConnect?.complete(null)
            runCatching { appContext.unbindService(this) }
        }

        override fun onNullBinding(name: ComponentName) {
            pendingConnect?.complete(null)
            runCatching { appContext.unbindService(this) }
        }
    }
    @Volatile private var pendingConnect: CompletableDeferred<ICredentialManager?>? = null

    /** Connects if needed. Null means the SCM is unavailable (absent, refused, dead or too slow). */
    private suspend fun connect(context: Context): ICredentialManager? = connectLock.withLock {
        service?.takeIf { it.asBinder().isBinderAlive }?.let { return@withLock it }
        appContext = context.applicationContext
        val waiter = CompletableDeferred<ICredentialManager?>()
        pendingConnect = waiter
        val intent = Intent().setComponent(ComponentName(SCM_PACKAGE, SCM_SERVICE))
        val bound = try {
            appContext.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            false
        }
        if (!bound) {
            runCatching { appContext.unbindService(connection) }
            return@withLock null
        }
        val connected = withTimeoutOrNull(CONNECT_TIMEOUT_MS) { waiter.await() }
        if (connected == null) runCatching { appContext.unbindService(connection) }
        pendingConnect = null
        connected
    }

    /** Whether the SCM can be reached right now. */
    suspend fun isAvailable(context: Context): Boolean = connect(context) != null

    /** The WebIDs stored in the SCM; null when the SCM is unavailable or did not answer. */
    suspend fun listWebIds(context: Context): List<String>? {
        val scm = connect(context) ?: return null
        val reply = CompletableDeferred<List<String>>()
        try {
            scm.listWebIds(object : IWebIdListCallback.Stub() {
                override fun onWebIds(webIds: MutableList<String>?) {
                    reply.complete(webIds.orEmpty())
                }
            })
        } catch (e: RemoteException) {
            return null
        }
        return withTimeoutOrNull(REQUEST_TIMEOUT_MS) { reply.await() }
    }

    /** Requests a token for [webId]; concurrent requests for the same WebID share one call. */
    suspend fun requestToken(context: Context, webId: String): ScmReply {
        appContext = context.applicationContext
        return requests.request(webId)
    }

    private suspend fun sendTokenRequest(webId: String): ScmReply {
        val scm = connect(appContext) ?: return ScmReply.Unavailable
        val reply = CompletableDeferred<Bundle>()
        try {
            scm.requestToken(webId, object : ITokenCallback.Stub() {
                override fun onResult(result: Bundle?) {
                    reply.complete(result ?: Bundle())
                }
            })
        } catch (e: RemoteException) {
            Log.w(TAG, "SCM went away during a request")
            return ScmReply.Unavailable
        }
        val result = withTimeoutOrNull(REQUEST_TIMEOUT_MS) { reply.await() } ?: return ScmReply.TransientError
        @Suppress("DEPRECATION")
        val action = result.getParcelable<PendingIntent>("action")
        return ScmReply.parse(result.getString("reply"), action)
    }
}
