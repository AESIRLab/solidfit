package com.example.solidfit

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavController
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.openid.appauth.GrantTypeValues
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.aesirlab.mylibrary.generateAuthString
import org.json.JSONObject
import com.example.solidfit.data.AuthTokenStore
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jose.util.Base64URL
import java.security.MessageDigest
import java.util.Date
import java.util.UUID

private const val REFRESH_TAG = "TokenRefresh"

// DPoP proof for the token endpoint (no "ath" needed here)
private fun buildTokenEndpointDPoP(method: String, url: String, signerJwk: String): String {
    val ec = ECKey.parse(signerJwk)
    val signer = ECDSASigner(ec.toECPrivateKey())

    val header = JWSHeader.Builder(JWSAlgorithm.ES256)
        .type(JOSEObjectType("dpop+jwt"))
        .jwk(ec.toPublicJWK())
        .build()

    val claims = JWTClaimsSet.Builder()
        .jwtID(UUID.randomUUID().toString())
        .issueTime(Date())
        .claim("htu", url)
        .claim("htm", method.uppercase())
        .build()

    val jwt = SignedJWT(header, claims)
    jwt.sign(signer)
    return jwt.serialize()
}

suspend fun tryRefreshTokens(tokenStore: AuthTokenStore): Boolean = withContext(Dispatchers.IO) {
    try {
        val refreshToken = tokenStore.getRefreshToken().first()
        if (refreshToken.isBlank()) {
            Log.d(REFRESH_TAG, "No refresh_token stored")
            return@withContext false
        }

        val tokenUrl = tokenStore.getTokenUri().first()
        val clientId = tokenStore.getClientId().first()
        val clientSecret = tokenStore.getClientSecret().first().takeIf { it.isNotBlank() }
        val signerJwk = tokenStore.getSigner().first()

        if (tokenUrl.isBlank() || clientId.isBlank() || signerJwk.isBlank()) {
            Log.d(REFRESH_TAG, "Missing tokenUrl/clientId/signerJwk")
            return@withContext false
        }

        val bodyBuilder = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("refresh_token", refreshToken)
            .add("client_id", clientId)

        if (clientSecret != null) {
            bodyBuilder.add("client_secret", clientSecret)
        }

        val request = Request.Builder()
            .url(tokenUrl)
            .addHeader("Accept", "*/*")
            .addHeader("Content-Type", "application/x-www-form-urlencoded")
            .addHeader("DPoP", buildTokenEndpointDPoP("POST", tokenUrl, signerJwk))
            .post(bodyBuilder.build())
            .build()

        val response = getUnsafeOkHttpClient().newCall(request).execute()
        val responseBody = response.body?.string().orEmpty()

        if (!response.isSuccessful) {
            Log.d(REFRESH_TAG, "Refresh failed ${response.code}: $responseBody")
            return@withContext false
        }

        val json = JSONObject(responseBody)

        val newAccessToken = json.optString("access_token")
        if (newAccessToken.isBlank()) return@withContext false
        tokenStore.setAccessToken(newAccessToken)

        // Refresh token may rotate; keep old if missing
        val newRefreshToken = json.optString("refresh_token")
        if (newRefreshToken.isNotBlank()) tokenStore.setRefreshToken(newRefreshToken)

        val now = System.currentTimeMillis()

        // expires_in is seconds
        val expiresInSec = json.optLong("expires_in", 0L)
        if (expiresInSec > 0L) {
            tokenStore.setTokenExpiresAt(now + expiresInSec * 1000L)
        }

        // If id_token present, prefer its exp
        val newIdToken = json.optString("id_token")
        if (newIdToken.isNotBlank()) {
            tokenStore.setIdToken(newIdToken)

            runCatching {
                val jwt = SignedJWT.parse(newIdToken)
                val expMillis = jwt.jwtClaimsSet.expirationTime?.time ?: 0L
                if (expMillis > 0L) tokenStore.setTokenExpiresAt(expMillis)

                val webId = JSONObject(jwt.payload.toJSONObject()).optString("webid")
                if (webId.isNotBlank()) tokenStore.setWebId(webId)
            }
        }

        Log.d(REFRESH_TAG, "Refresh succeeded, expiresAt updated")
        true
    } catch (e: Exception) {
        Log.d(REFRESH_TAG, "Refresh exception: ${e.message}")
        false
    }
}

fun buildResourceDPoP(method: String, url: String, accessToken: String, signerJwk: String): String {
    val ec = ECKey.parse(signerJwk)
    val signer = ECDSASigner(ec.toECPrivateKey())
    val header = JWSHeader.Builder(JWSAlgorithm.ES256)
        .type(JOSEObjectType("dpop+jwt"))
        .jwk(ec.toPublicJWK())
        .build()
    val ath = Base64URL.encode(MessageDigest.getInstance("SHA-256").digest(accessToken.toByteArray())).toString()
    val claims = JWTClaimsSet.Builder()
        .jwtID(UUID.randomUUID().toString())
        .issueTime(Date())
        .claim("htu", url)
        .claim("htm", method.uppercase())
        .claim("ath", ath)
        .build()
    val jwt = SignedJWT(header, claims)
    jwt.sign(signer)
    return jwt.serialize()
}

suspend fun okHttpRequest(url: String): Response = withContext(Dispatchers.IO) {
    val client = getUnsafeOkHttpClient()
    val request = Request.Builder().url(url).build()
    val response = client.newCall(request).execute()
    return@withContext response
}

suspend fun preliminaryAuth(tokenStore: AuthTokenStore, code: String?)  {
    val clientId = tokenStore.getClientId().first()
    val rClientSecret = tokenStore.getClientSecret().first()
    val tokenUrl = tokenStore.getTokenUri().first()
    val codeVerifier = tokenStore.getCodeVerifier().first()
    val redirectUri = tokenStore.getRedirectUri().first()

    var clientSecret: String? = null
    if (rClientSecret != "") {
        clientSecret = rClientSecret
    }

    val authString = generateAuthString("POST", tokenUrl)

    val response = tokenRequest(
        clientId,
        clientSecret,
        tokenUrl,
        code!!,
        codeVerifier,
        redirectUri,
        authString
    )

    val json = JSONObject(response)
    val accessToken = json.getString("access_token")

    val idToken: String
    try {
        idToken = json.getString("id_token")
        tokenStore.setIdToken(idToken)

        try {
            val jwtObject = SignedJWT.parse(idToken)
            val body = jwtObject.payload
            val jsonBody = JSONObject(body.toJSONObject())
            val webId = jsonBody.getString("webid")
            tokenStore.setWebId(webId)
        } catch (e: Exception) {
            e.message?.let { Log.e("error", it) }
        }
    } catch (e: Exception) {
        e.message?.let { Log.e("error", it) }
    }

    val refreshToken: String
    try {
        refreshToken = json.getString("refresh_token")
        tokenStore.setRefreshToken(refreshToken)
    } catch (e: Exception){
        e.message?.let { Log.d("error", it) }
    }

    tokenStore.setAccessToken(accessToken)
}

fun getUnsafeOkHttpClient(): OkHttpClient {
    val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        }

        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        }

        override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
    })

    // Install the all-trusting trust manager
    val sslContext = SSLContext.getInstance("TLS")
    sslContext.init(null, trustAllCerts, java.security.SecureRandom())
    // Create an ssl socket factory with our all-trusting manager
    val sslSocketFactory = sslContext.socketFactory

    return OkHttpClient.Builder()
        .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
        .hostnameVerifier { _, _ -> true }.build()
}

private suspend fun tokenRequest(
    clientId: String,
    clientSecret: String?,
    tokenUrl: String,
    code: String,
    codeVerifier: String,
    redirectUri: String,
    authString: String
): String = withContext(Dispatchers.IO){
    val client = getUnsafeOkHttpClient()

    val bodyBuilder = FormBody.Builder()
        .add("grant_type", GrantTypeValues.AUTHORIZATION_CODE)
        .add("code_verifier", codeVerifier)
        .add("code", code)
        .add("redirect_uri", redirectUri)
        .add("client_id", clientId)

    if (clientSecret != null) {
        bodyBuilder.add("client_secret", clientSecret)
    }
    val body = bodyBuilder.build()

    val tokenRequest = Request.Builder()
        .url(tokenUrl)
        .addHeader("Accept", "*/*")
        .addHeader("DPoP", authString)
        .addHeader("Content-Type", "application/x-www-form-urlencoded")
        .post(body)
        .build()
    val response = client.newCall(tokenRequest).execute()
    val responseBody = response.body!!.string()
    Log.d("response body", JSONObject(responseBody).toString())

    return@withContext responseBody
}

