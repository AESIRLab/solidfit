package com.example.solidfit.screens

import android.app.Activity
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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

    LaunchedEffect(code) {
        withContext(Dispatchers.IO) {
            preliminaryAuth(tokenStore, code)
        }

        onFinishedAuth()
    }
}

private suspend fun preliminaryAuth(tokenStore: AuthTokenStore, code: String?) {
    require(!code.isNullOrBlank()) { "Missing authorization code in redirect" }

    val clientId = tokenStore.getClientId().first()
    val rClientSecret = tokenStore.getClientSecret().first()
    val tokenUrl = tokenStore.getTokenUri().first()
    val codeVerifier = tokenStore.getCodeVerifier().first()
    val redirectUri = tokenStore.getRedirectUri().first()

    val clientSecret = rClientSecret.takeIf { it.isNotBlank() }

    val dpop = generateDPoPKey()
    tokenStore.setSigner(dpop.toJSONObject().toString())

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
    val json = JSONObject(response.body!!.string())

    val accessToken = json.getString("access_token")
    tokenStore.setAccessToken(accessToken)

    json.optString("refresh_token").takeIf { it.isNotBlank() }?.let {
        tokenStore.setRefreshToken(it)
    }

    val idToken = json.optString("id_token")
    if (idToken.isNotBlank()) {
        tokenStore.setIdToken(idToken)

        try {
            val jwtObject = SignedJWT.parse(idToken)

            val expiresInSec = json.optLong("expires_in", 0L)
            if (expiresInSec > 0L) {
                val fallbackExp = System.currentTimeMillis() + (expiresInSec * 1000L)
                tokenStore.setTokenExpiresAt(fallbackExp)
                Log.d(TAG, "expiresAt (expires_in)=$fallbackExp")
            }

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
            Log.d(TAG, "JWT parse error: ${e.message}")
        }
    }
}
