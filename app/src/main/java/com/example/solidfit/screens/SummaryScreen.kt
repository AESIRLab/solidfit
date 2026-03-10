package com.example.solidfit.screens

import android.app.Application
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.solidfit.WorkoutItemSolidApplication
import com.example.solidfit.data.AuthTokenStore
import com.example.solidfit.data.Utilities.Companion.ABSOLUTE_URI
import com.example.solidfit.data.generatePostRequest
import com.example.solidfit.data.generatePutRequest
import com.example.solidfit.data.getStorage
import com.example.solidfit.getUnsafeOkHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private const val TAG = "SummaryScreen"

class SummaryScreen(
    private val application: Application
) {
    @Composable
    fun Summary() {
        val coroutineScope = rememberCoroutineScope()
        val app = application as WorkoutItemSolidApplication
        val store = AuthTokenStore(LocalContext.current.applicationContext)
        val accessToken by store.getAccessToken().collectAsState(initial = "")
        val webId by store.getWebId().collectAsState(initial = "")
        val signingJwk by store.getSigner().collectAsState(initial = "")
        val expirationTime by store.getTokenExpiresAt().collectAsState(initial = 0L)

        val summaryText by store.getSummaryText().collectAsState(initial = "")

        var isLoading by remember { mutableStateOf(false) }
        var resultText by remember { mutableStateOf("") }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Workout Summary",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                enabled = !isLoading && accessToken.isNotBlank() && signingJwk.isNotBlank(),
                onClick = {
                    coroutineScope.launch {
                        isLoading = true
                        resultText = "Requesting summary"
                        try {
                            val responseCode = withContext(Dispatchers.IO) {
                                val storageUri = getStorage(webId)
                                val resourceUri = "${storageUri}AndroidApplication/ButtonClick/"

                                val json = JSONObject()
                                json.put("action", "/ButtonClick/")
                                json.put("webId", webId)
                                val body = json.toString()
                                    .toRequestBody("application/json".toMediaType())

                                val request = generatePostRequest(resourceUri, body, accessToken, signingJwk, "application/json")
                                val client = getUnsafeOkHttpClient()
                                val response = client.newCall(request).execute()
                                val code = response.code
                                response.close()
                                code
                            }
                            resultText = if (responseCode in 200..299) {
                                "Summary requested successfully (HTTP $responseCode)"
                            } else {
                                "Request failed with HTTP $responseCode"
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "PUT request failed", e)
                            resultText = "Error: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                }
            ) {
                Text("Request Summary")
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isLoading) {
                CircularProgressIndicator()
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (resultText.isNotBlank()) {
                Text(
                    text = resultText,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = if (summaryText.isNotBlank()) summaryText else "No summary available yet. Request one above.",
                fontSize = 16.sp
            )
        }
    }
}
