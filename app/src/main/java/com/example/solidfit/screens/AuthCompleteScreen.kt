package com.example.solidfit.screens

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import com.example.solidfit.data.AuthTokenStore
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.aesirlab.mylibrary.generateDPoPKey
import org.aesirlab.mylibrary.sharedfunctions.buildTokenRequest
import org.aesirlab.mylibrary.sharedfunctions.createUnsafeOkHttpClient
import org.json.JSONObject

private const val TAG = "AuthCompleteScreen"

@Composable
fun AuthCompleteScreen(
    tokenStore: AuthTokenStore,
    onFinishedAuth: () -> Unit,
) {
    val context = LocalContext.current
    val intentData = (context as Activity).intent.data
    val code = intentData?.getQueryParameter("code")

    // Prevent exchanging the same code multiple times
    var didExchange by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(code) {
        if (code.isNullOrBlank()) {
            Log.e(TAG, "Missing authorization code in redirect")
            return@LaunchedEffect
        }

        if (didExchange) {
            Log.d(TAG, "Auth code already exchanged; skipping")
            return@LaunchedEffect
        }
        didExchange = true

        val ok = withContext(Dispatchers.IO) {
            preliminaryAuth(tokenStore, code)
        }

        if (ok) {
            onFinishedAuth()
        } else {
            // Stay on this screen; you can add UI/Toast if you want.
            Log.e(TAG, "Auth failed; not navigating forward")
        }
    }
}

/**
 * Returns true if we successfully stored an access token (and optional webid/expires).
 */
private suspend fun preliminaryAuth(tokenStore: AuthTokenStore, code: String): Boolean {
    val clientId = tokenStore.getClientId().first()
    val rClientSecret = tokenStore.getClientSecret().first()
    val tokenUrl = tokenStore.getTokenUri().first()
    val codeVerifier = tokenStore.getCodeVerifier().first()
    val redirectUri = tokenStore.getRedirectUri().first()

    val clientSecret = rClientSecret.takeIf { it.isNotBlank() }

    val dpop = generateDPoPKey()
    tokenStore.setSigner(JSONObject(dpop.toJSONObject()).toString())

    val tokenRequest = buildTokenRequest(
        clientId,
        tokenUrl,
        codeVerifier,
        redirectUri,
        dpop,
        clientSecret,
        code
    )

    val response = createUnsafeOkHttpClient().newCall(tokenRequest).execute()
    val bodyString = response.body?.string().orEmpty()

    if (!response.isSuccessful) {
        Log.e(TAG, "Token HTTP ${response.code}. Body=$bodyString")
        // Try to parse error JSON if present
        runCatching {
            val j = JSONObject(bodyString)
            if (j.has("error")) {
                Log.e(TAG, "Token exchange failed: ${j.optString("error")} ${j.optString("error_description")}")
            }
        }
        return false
    }

    val json = runCatching { JSONObject(bodyString) }.getOrElse {
        Log.e(TAG, "Token response not JSON. Body=$bodyString")
        return false
    }

    // Handle OAuth error responses even if HTTP 200 happens
    if (json.has("error")) {
        Log.e(TAG, "Token exchange failed: ${json.optString("error")} ${json.optString("error_description")}")
        return false
    }

    val accessToken = json.optString("access_token", "")
    if (accessToken.isBlank()) {
        Log.e(TAG, "Token response missing access_token. Body=$bodyString")
        return false
    }

    tokenStore.setAccessToken(accessToken)

    json.optString("refresh_token").takeIf { it.isNotBlank() }?.let {
        tokenStore.setRefreshToken(it)
    }

    // expires_in handling (fallback if id_token parsing fails)
    val expiresInSec = json.optLong("expires_in", 0L)
    if (expiresInSec > 0L) {
        val expMillis = System.currentTimeMillis() + (expiresInSec * 1000L)
        tokenStore.setTokenExpiresAt(expMillis)
        Log.d(TAG, "expiresAt (expires_in)=$expMillis")
    }

    val idToken = json.optString("id_token")
    if (idToken.isNotBlank()) {
        tokenStore.setIdToken(idToken)

        try {
            val jwtObject = SignedJWT.parse(idToken)
            val expMillis = jwtObject.jwtClaimsSet.expirationTime?.time ?: 0L
            if (expMillis > 0L) {
                tokenStore.setTokenExpiresAt(expMillis)
                Log.d(TAG, "expiresAt (id_token)=$expMillis")
            }

            val jsonBody = JSONObject(jwtObject.payload.toJSONObject())
            val webId = jsonBody.optString("webid")
            if (webId.isNotBlank()) {
                tokenStore.setWebId(webId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "JWT parse error: ${e.message}")
        }
    }

    return true
}
