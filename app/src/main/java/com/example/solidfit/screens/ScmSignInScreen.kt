package com.example.solidfit.screens

import android.app.Activity
import android.app.ActivityOptions
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.solidfit.data.AuthTokenStore
import com.example.solidfit.data.ScmClient
import com.example.solidfit.data.ScmReply
import com.example.solidfit.data.SignInProblem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val SCM_PACKAGE = "com.zybooks.solidcredentialmanager"
private const val SCM_EXTRA_WEBID = "com.zybooks.solidcredentialmanager.extra.WEBID"
/** The SCM approval screen's result code for "denied" (Activity.RESULT_FIRST_USER). */
private const val RESULT_DENIED = Activity.RESULT_FIRST_USER

private sealed interface ScmSignInState {
    data object Checking : ScmSignInState
    data object Loading : ScmSignInState
    data object Empty : ScmSignInState
    data object ListError : ScmSignInState
    data class Picker(val webIds: List<String>) : ScmSignInState
    data class Requesting(val webId: String) : ScmSignInState
    data class Problem(val problem: SignInProblem, val webId: String) : ScmSignInState
    data class Failed(val message: String, val webId: String) : ScmSignInState
}

/**
 * Sign-in through the Solid Credential Manager: pick a WebID stored there, approve this app in
 * the SCM, and receive a token. Falls back to [onUseBrowser] only when the SCM can't be reached.
 */
@Composable
fun ScmSignInScreen(
    tokenStore: AuthTokenStore,
    onSignedIn: () -> Unit,
    onUseBrowser: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<ScmSignInState>(ScmSignInState.Checking) }
    // Which reply sent the user to the SCM, to word a cancellation correctly.
    var pendingProblem by remember { mutableStateOf(SignInProblem.ACCESS_DENIED) }

    fun loadWebIds() {
        scope.launch {
            state = ScmSignInState.Loading
            if (!ScmClient.isAvailable(context)) {
                onUseBrowser()
                return@launch
            }
            val webIds = ScmClient.listWebIds(context)
            state = when {
                webIds == null -> ScmSignInState.ListError
                webIds.isEmpty() -> ScmSignInState.Empty
                else -> ScmSignInState.Picker(webIds)
            }
        }
    }

    lateinit var requestToken: (String) -> Unit

    val approvalLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        scope.launch {
            val webId = tokenStore.getWaitingForScm().first()
            tokenStore.setWaitingForScm("")
            when (result.resultCode) {
                // Only an approval retries automatically; anything else shows the problem screen.
                Activity.RESULT_OK -> requestToken(result.data?.getStringExtra(SCM_EXTRA_WEBID) ?: webId)
                RESULT_DENIED -> state = ScmSignInState.Problem(SignInProblem.ACCESS_DENIED, webId)
                else -> state = ScmSignInState.Problem(pendingProblem, webId)
            }
        }
    }

    requestToken = { webId ->
        scope.launch {
            state = ScmSignInState.Requesting(webId)
            when (val reply = ScmClient.requestToken(context, webId)) {
                is ScmReply.Granted -> {
                    tokenStore.saveScmSession(reply.webId, reply.accessToken, reply.dpopKey, reply.expiresAt, System.currentTimeMillis())
                    onSignedIn()
                }
                is ScmReply.ConsentRequired, is ScmReply.LoginRequired -> {
                    val action = (reply as? ScmReply.ConsentRequired)?.action ?: (reply as ScmReply.LoginRequired).action
                    pendingProblem = if (reply is ScmReply.LoginRequired) SignInProblem.NEEDS_LOGIN else SignInProblem.ACCESS_DENIED
                    if (action == null) {
                        state = ScmSignInState.Failed("The Credential Manager didn't send its approval screen.", webId)
                    } else {
                        tokenStore.setWaitingForScm(webId)
                        approvalLauncher.launch(IntentSenderRequest.Builder(action).build(), allowScmToOpenItsScreen())
                    }
                }
                ScmReply.UnknownWebId -> loadWebIds()
                ScmReply.Denied -> state = ScmSignInState.Problem(SignInProblem.ACCESS_DENIED, webId)
                ScmReply.TransientError -> state = ScmSignInState.Failed("Couldn't get a token from the Credential Manager. Try again.", webId)
                ScmReply.Unavailable -> onUseBrowser()
            }
        }
    }

    LaunchedEffect(Unit) {
        val (problemName, problemWebId) = tokenStore.getSignInProblem().first()
        val problem = SignInProblem.entries.firstOrNull { it.name == problemName }
        val waiting = tokenStore.getWaitingForScm().first()
        when {
            problem != null -> state = ScmSignInState.Problem(problem, problemWebId)
            // The app was closed while the SCM screen was open: ask again.
            waiting.isNotBlank() -> {
                tokenStore.setWaitingForScm("")
                requestToken(waiting)
            }
            else -> loadWebIds()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        if (state == ScmSignInState.Empty) loadWebIds()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        when (val current = state) {
            ScmSignInState.Checking, ScmSignInState.Loading, is ScmSignInState.Requesting -> CircularProgressIndicator()
            ScmSignInState.Empty -> {
                Text("The Solid Credential Manager has no WebIDs yet.")
                Button(onClick = {
                    context.packageManager.getLaunchIntentForPackage(SCM_PACKAGE)?.let(context::startActivity)
                }) { Text("Add a WebID in the Credential Manager") }
            }
            ScmSignInState.ListError -> {
                Text("Couldn't load your WebIDs from the Credential Manager.")
                Button(onClick = ::loadWebIds) { Text("Try again") }
            }
            is ScmSignInState.Picker -> {
                Text("Sign in with a WebID from the Solid Credential Manager", style = MaterialTheme.typography.titleMedium)
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(current.webIds) { webId ->
                        OutlinedButton(onClick = { requestToken(webId) }, modifier = Modifier.fillMaxWidth()) { Text(webId) }
                    }
                }
            }
            is ScmSignInState.Problem -> SignInProblemContent(
                problem = current.problem,
                webId = current.webId,
                onRetry = {
                    scope.launch {
                        tokenStore.setSignInProblem("", "")
                        requestToken(current.webId)
                    }
                },
                onChooseAnother = {
                    scope.launch {
                        tokenStore.setSignInProblem("", "")
                        loadWebIds()
                    }
                },
            )
            is ScmSignInState.Failed -> {
                Text(current.message)
                Button(onClick = { requestToken(current.webId) }) { Text("Try again") }
            }
        }
    }
}

/** Why the app isn't signed in, with a retry that goes back through the Credential Manager. */
@Composable
private fun SignInProblemContent(
    problem: SignInProblem,
    webId: String,
    onRetry: () -> Unit,
    onChooseAnother: () -> Unit,
) {
    Text(problem.message)
    Text(webId, style = MaterialTheme.typography.bodySmall)
    Button(onClick = onRetry) { Text("Try again") }
    OutlinedButton(onClick = onChooseAnother) { Text("Choose another WebID") }
}

/**
 * Since Android 14 an app sending another app's PendingIntent must opt in to letting it open an
 * activity; without this the SCM's approval screen can be blocked.
 */
private fun allowScmToOpenItsScreen(): ActivityOptionsCompat? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
    return object : ActivityOptionsCompat() {
        override fun toBundle(): Bundle = ActivityOptions.makeBasic()
            .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            .toBundle()
    }
}
