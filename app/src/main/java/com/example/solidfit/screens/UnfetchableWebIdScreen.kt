package com.example.solidfit.screens

import com.example.solidfit.OIDC_REDIRECT_URI
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.solidfit.R
import com.example.solidfit.StartButton
import com.example.solidfit.getUnsafeOkHttpClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import net.openid.appauth.CodeVerifierUtil
import org.aesirlab.mylibrary.buildAuthorizationUrl
import org.aesirlab.mylibrary.buildConfigRequest
import org.aesirlab.mylibrary.buildRegistrationJSONBody
import org.aesirlab.mylibrary.buildRegistrationRequest
import com.example.solidfit.data.AuthTokenStore

@Composable
fun UnfetchableWebIdScreen(
    tokenStore: AuthTokenStore,
    renderError: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement =  Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        horizontalAlignment =  Alignment.CenterHorizontally,
    ) {
        val appTitle = "SolidFit"
        var solidProvider by rememberSaveable {
            mutableStateOf("")
        }

        Image(
            painter = painterResource(id = R.drawable.exercise_black_46dp),
            contentDescription = "App logo"
        )
        Text(
            text=appTitle,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp
        )
        TextField(
            value = solidProvider,
            onValueChange =  { solidProvider = it },
            label = { Text("Solid Provider", fontStyle = FontStyle.Italic) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )
        val context = LocalContext.current
        StartButton(text = "Fetch OIDC Config") {
            val client = getUnsafeOkHttpClient()
            val redirectUris = listOf(OIDC_REDIRECT_URI)
            CoroutineScope(Dispatchers.IO).launch {
              try {

                  val configRequest = buildConfigRequest(solidProvider)
                  val configResponse = client.newCall(configRequest).execute()

                  val responseBody = configResponse.body!!.string()
                  val configJSON = JSONObject(responseBody)

                  val registrationEndpoint = configJSON.getString("registration_endpoint")
                  val tokenEndpoint = configJSON.getString("token_endpoint")
                  val authUrl = configJSON.getString("authorization_endpoint")

                  tokenStore.setTokenUri(tokenEndpoint)

                  val jsonBody = buildRegistrationJSONBody(appTitle, redirectUris)
                  val registrationRequest = buildRegistrationRequest(registrationEndpoint, jsonBody)
                  val registrationResponse = client.newCall(registrationRequest).execute()
                  val registrationString = registrationResponse.body!!.string()

                  val registrationJSON = JSONObject(registrationString)
                  val clientId = registrationJSON.getString("client_id")
                  // Always overwrite, so a secret from an earlier registration is never sent with this client_id
                  val clientSecret = registrationJSON.optString("client_secret").takeIf { it.isNotBlank() }
                  tokenStore.setClientSecret(clientSecret ?: "")
                  tokenStore.setClientId(clientId)

                  val codeVerifier = CodeVerifierUtil.generateRandomCodeVerifier()
                  val codeVerifierChallenge = CodeVerifierUtil.deriveCodeVerifierChallenge(codeVerifier)

                  tokenStore.setCodeVerifier(codeVerifier)

                  val authorizationUrl = buildAuthorizationUrl(authUrl, clientId, codeVerifierChallenge, redirectUris[0], clientSecret)
                  val sendUri = Uri.parse(authorizationUrl.toString())
                  context.startActivity(Intent(Intent.ACTION_VIEW, sendUri))
              } catch (e: CancellationException) {
                throw e
              } catch (e: Exception) {
                Log.e("UnfetchableWebIdScreen", "Sign-in setup failed", e)
                renderError(e.message ?: "Sign-in failed. Please try again.")
              }
            }
        }
    }
}