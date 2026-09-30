package com.example.solidfit.data

import android.content.*
import android.net.Uri
import android.os.IBinder
import android.util.Log
import com.zybooks.solidcredentialmanager.IAccessRequestCallback
import com.zybooks.solidcredentialmanager.IAidlTestCredentialService
import com.zybooks.solidcredentialmanager.IWebIdRequestCallback
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.atomic.AtomicReference

class CredentialServiceClient(
    private val appContext: Context
) {
    @Volatile private var service: IAidlTestCredentialService? = null

    private val pendingResponse = AtomicReference<CompletableDeferred<String>?>(null)

    private val accessCb = object : IAccessRequestCallback.Stub() {
        override fun onResponse(response: String) {
            val raw = response.trim()
            Log.d("CredentialServiceClient", "FINAL RESPONSE: $raw")

            // Ignore intermediate status spam
            if (raw.lowercase().startsWith("registered access request")) return

            val deferred = pendingResponse.get() ?: return

            // ✅ If the server returns JSON, treat as final
            if (raw.startsWith("{")) {
                pendingResponse.set(null)
                deferred.complete(raw)
                return
            }

            // ✅ Otherwise handle the legacy strings
            val msg = raw.lowercase()
            when (msg) {
                "access granted" -> {
                    pendingResponse.set(null)
                    deferred.complete("""{"status":"granted"}""")
                }
                "access denied" -> {
                    pendingResponse.set(null)
                    deferred.complete("""{"status":"denied"}""")
                }
                else -> {
                    // If the server returns a terminal error message, propagate it
                    // (this is IMPORTANT for "access granted but no stored credentials ...")
                    if (msg.startsWith("access granted but no stored credentials") ||
                        msg.startsWith("failed") ||
                        msg.startsWith("error")
                    ) {
                        pendingResponse.set(null)
                        deferred.complete("""{"status":"error","message":${org.json.JSONObject.quote(raw)}}""")
                    }
                    // else ignore
                }
            }
        }

        override fun onError(message: String) {
            pendingResponse.getAndSet(null)
                ?.complete("""{"status":"error","message":${org.json.JSONObject.quote(message)}}""")
        }

        override fun basicTypes(
            anInt: Int, aLong: Long, aBoolean: Boolean, aFloat: Float, aDouble: Double, aString: String?
        ) = Unit
    }


    private val conn = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            service = IAidlTestCredentialService.Stub.asInterface(binder)
            // Register callback once after connect
            service?.registerAccessRequestCallback(accessCb)
        }

        override fun onServiceDisconnected(name: ComponentName) {
            service = null
            pendingResponse.getAndSet(null)?.complete(
                """{"status":"error","message":"service disconnected"}"""
            )
        }
    }

    fun bind() {
        val intent = Intent().apply {
            component = ComponentName(
                "com.zybooks.solidcredentialmanager",
                "com.zybooks.solidcredentialmanager.services.TestCredentialService"
            )
        }
        appContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)
    }

    fun unbind() {
        runCatching { appContext.unbindService(conn) }
        service = null
    }

    suspend fun fetchWebIds(): List<String> {
        val svc = service ?: throw IllegalStateException("Credential service not connected")

        val deferred = CompletableDeferred<String>()
        val cb = object : IWebIdRequestCallback.Stub() {
            override fun onResponse(response: String) {
                deferred.complete(response)
            }
            override fun basicTypes(
                anInt: Int, aLong: Long, aBoolean: Boolean, aFloat: Float, aDouble: Double, aString: String?
            ) = Unit
        }

        svc.getWebIds(cb)
        return parseWebIdsFromServerString(deferred.await())
    }

    /**
     * Returns the final server reply:
     *  - "access denied"
     *  - JSON string (preferred)
     *  - (legacy) "access granted"
     */
    suspend fun requestCredentialsJson(webId: String): String {
        val svc = service ?: throw IllegalStateException("Credential service not connected")
        check(pendingResponse.get() == null) { "Access request already in flight" }

        val deferred = CompletableDeferred<String>()
        pendingResponse.set(deferred)

        svc.requestWebId(webId, appContext.packageName, System.currentTimeMillis())

        return deferred.await()
    }

    private fun normalizeWebIdKey(input: String): String {
        // remove trailing slash only (common mismatch)
        var w = input.trim()
        while (w.endsWith("/")) w = w.dropLast(1)
        return w
    }

    private fun credentialUriForWebId(webId: String): Uri {
        val base = Uri.parse("content://com.zybooks.solidcredentialmanager.contentprovider/webids")
        val encoded = Uri.encode(webId)
        // content://.../webids/<webId>/credentials/
        return base.buildUpon()
            .appendPath(encoded)
            .appendPath("credentials")
            .appendPath("") // ensures trailing slash
            .build()
    }


    private fun parseWebIdsFromServerString(raw: String): List<String> {
        // Example raw: "id1;, id2;, id3;"
        return raw
            .split(';')
            .map { it.trim().trim(',') }   // remove trailing ","
            .filter { it.isNotBlank() }
    }
}
