package com.example.solidfit.data

import kotlinx.coroutines.flow.first

/** Why an SCM session ended, as shown on the sign-in problem screen. */
enum class SignInProblem(val message: String) {
    ACCESS_DENIED("Access to this WebID was denied or revoked in the Solid Credential Manager."),
    NEEDS_LOGIN("This WebID needs you to log in again in the Solid Credential Manager."),
}

/** What a background token refresh should do with the SCM's reply. */
sealed interface BackgroundOutcome {
    data class Save(val grant: ScmReply.Granted) : BackgroundOutcome
    /** Keep using the current token; it is still valid. */
    data object Keep : BackgroundOutcome
    /** Sign out; [problem] is shown when set, otherwise the user goes back to the WebID picker. */
    data class SignOut(val problem: SignInProblem?) : BackgroundOutcome
}

/**
 * Background refreshes cannot show the SCM's approval screen, so anything that needs the user
 * signs the app out to a screen whose retry can. A temporary failure, or the SCM disappearing,
 * keeps the current token until it expires; the app never falls back to the browser on its own.
 */
fun backgroundOutcome(reply: ScmReply, currentExpiresAt: Long, now: Long): BackgroundOutcome = when (reply) {
    is ScmReply.Granted -> BackgroundOutcome.Save(reply)
    ScmReply.TransientError, ScmReply.Unavailable ->
        if (currentExpiresAt > now) BackgroundOutcome.Keep else BackgroundOutcome.SignOut(null)
    is ScmReply.ConsentRequired, ScmReply.Denied -> BackgroundOutcome.SignOut(SignInProblem.ACCESS_DENIED)
    is ScmReply.LoginRequired -> BackgroundOutcome.SignOut(SignInProblem.NEEDS_LOGIN)
    ScmReply.UnknownWebId -> BackgroundOutcome.SignOut(null)
}

/**
 * Gets a fresh token for the signed-in SCM session. Returns the access token to use, or null
 * when the session ended (the caller signs out).
 */
suspend fun refreshScmSession(tokenStore: AuthTokenStore, now: () -> Long = System::currentTimeMillis): String? {
    val webId = tokenStore.getWebId().first()
    if (webId.isBlank()) return null
    val reply = ScmClient.requestToken(tokenStore.context, webId)
    return when (val outcome = backgroundOutcome(reply, tokenStore.getTokenExpiresAt().first(), now())) {
        is BackgroundOutcome.Save -> {
            val grant = outcome.grant
            tokenStore.saveScmSession(grant.webId, grant.accessToken, grant.dpopKey, grant.expiresAt, now())
            grant.accessToken
        }
        BackgroundOutcome.Keep -> tokenStore.getAccessToken().first()
        is BackgroundOutcome.SignOut -> {
            outcome.problem?.let { tokenStore.setSignInProblem(it.name, webId) }
            null
        }
    }
}
