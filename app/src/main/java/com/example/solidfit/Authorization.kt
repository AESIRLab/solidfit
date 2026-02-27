package com.example.solidfit

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.solidfit.data.AuthTokenStore
import com.example.solidfit.data.CredentialServiceClient
import com.example.solidfit.healthdata.HealthConnectManager
import com.example.solidfit.screens.UpdateWorkouts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.solidfit.screens.StartAuthScreen

// All apps screens
enum class SolidAuthFlowScreen {
    ActiveSessionScreen,
    AddEditWorkoutScreen,
    AuthCompleteScreen,
    HeartRateMonitor,
    LandingScreen,
    SettingsScreen,
    StartAuthScreen,
    WeightMonitor,
    WorkoutList,
    WorkoutCardScreen,
    UnfetchableWebIdScreen,
    UpdateWorkouts,
    CredentialManagerAuthScreen,
}


// Used for navbar
sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    data object WorkoutList : BottomNavItem(
        route = SolidAuthFlowScreen.WorkoutList.name,
        title = "Workout List",
        icon = Icons.AutoMirrored.Filled.List
    )
    data object WeightMonitor: BottomNavItem(
        route = SolidAuthFlowScreen.WeightMonitor.name,
        title = "Weight Monitor",
        icon = Icons.Default.Person
    )
    data object Settings: BottomNavItem(
        route = SolidAuthFlowScreen.SettingsScreen.name,
        title = "Settings",
        icon = Icons.Default.Settings
    )

}

@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter", "NewApi")
@Composable
fun Authorization(
    healthConnectManager: HealthConnectManager,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val tokenStore = remember { AuthTokenStore(context.applicationContext) }

    NavHost(
        navController = navController,
        startDestination = SolidAuthFlowScreen.LandingScreen.name
    ) {
        composable(SolidAuthFlowScreen.LandingScreen.name) {
            LandingGate(
                tokenStore = tokenStore,
                onValidToken = {
                    navController.navigate(SolidAuthFlowScreen.UpdateWorkouts.name) {
                        popUpTo(SolidAuthFlowScreen.LandingScreen.name) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onNeedsLogin = {
                    navController.navigate(SolidAuthFlowScreen.CredentialManagerAuthScreen.name) {
                        popUpTo(SolidAuthFlowScreen.LandingScreen.name) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(SolidAuthFlowScreen.CredentialManagerAuthScreen.name) {
            CredentialManagerLoginScreen(
                tokenStore = tokenStore,
                onLoginSuccess = {
                    navController.navigate(SolidAuthFlowScreen.UpdateWorkouts.name) {
                        popUpTo(SolidAuthFlowScreen.CredentialManagerAuthScreen.name) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        composable(SolidAuthFlowScreen.UpdateWorkouts.name) {
            RequireValidAuthToken(
                tokenStore = tokenStore,
                navController = navController
            ) {
                UpdateWorkouts(
                    healthConnectManager = healthConnectManager,
                    authNavController = navController
                )
            }
        }
    }
}


@Composable
private fun LandingGate(
    tokenStore: AuthTokenStore,
    onValidToken: () -> Unit,
    onNeedsLogin: () -> Unit,
) {
    // simple loading UI
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        CircularProgressIndicator()
        Text("Checking session…")
    }

    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        val skew = 60_000L // 1 minute

        suspend fun looksValid(): Boolean {
            val webId = tokenStore.getWebId().first()
            val accessToken = tokenStore.getAccessToken().first()
            val signer = tokenStore.getSigner().first()
            val expiresAt = tokenStore.getTokenExpiresAt().first()
            return webId.isNotBlank() &&
                    accessToken.isNotBlank() &&
                    signer.isNotBlank() &&
                    expiresAt > (System.currentTimeMillis() + skew)
        }

        if (looksValid()) {
            onValidToken()
            return@LaunchedEffect
        }

        // If not valid, try refresh before forcing login
        val refreshToken = tokenStore.getRefreshToken().first()
        val canRefresh = refreshToken.isNotBlank() && refreshToken != "null"

        if (canRefresh) {
            val refreshed = tryRefreshTokens(tokenStore)
            if (refreshed && looksValid()) {
                onValidToken()
                return@LaunchedEffect
            }
        }

        onNeedsLogin()
    }
}

@Composable
private fun CredentialManagerLoginScreen(
    tokenStore: AuthTokenStore,
    onLoginSuccess: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Service client (from your app code)
    val credentialClient = remember {
        CredentialServiceClient(context.applicationContext)
    }

    // Bind/unbind with lifecycle of this screen
    DisposableEffect(Unit) {
        credentialClient.bind()
        onDispose { credentialClient.unbind() }
    }

    var loadingWebIds by remember { mutableStateOf(true) }
    var webIds by remember { mutableStateOf<List<String>>(emptyList()) }
    var statusText by remember { mutableStateOf("Connecting to credential manager…") }
    var requestInFlight by remember { mutableStateOf(false) }

    // Fetch available WebIDs once service is up
    LaunchedEffect(Unit) {
        loadingWebIds = true
        statusText = "Loading WebIDs…"

        // The bind is async; this “retry loop” avoids crashing if the service
        // isn't connected yet when fetchWebIds() runs.
        repeat(20) {
            try {
                webIds = withContext(Dispatchers.IO) { credentialClient.fetchWebIds() }
                loadingWebIds = false
                statusText = if (webIds.isEmpty()) "No WebIDs found in credential manager." else "Select a WebID:"
                return@LaunchedEffect
            } catch (e: Throwable) {
                delay(150)
            }
        }

        loadingWebIds = false
        statusText = "Credential service not ready (timed out)."
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(statusText)

        if (loadingWebIds) {
            CircularProgressIndicator()
            return@Column
        }

        if (webIds.isEmpty()) {
            Button(
                enabled = !requestInFlight,
                onClick = {
                    // re-try fetching webids
                    coroutineScope.launch {
                        loadingWebIds = true
                        try {
                            webIds = withContext(Dispatchers.IO) { credentialClient.fetchWebIds() }
                            statusText = if (webIds.isEmpty()) "No WebIDs found in credential manager." else "Select a WebID:"
                        } catch (e: Throwable) {
                            statusText = "Failed to fetch WebIDs: ${e.message}"
                        } finally {
                            loadingWebIds = false
                        }
                    }
                }
            ) { Text("Retry") }
            return@Column
        }

        webIds.forEach { webId ->
            Button(
                enabled = !requestInFlight,
                onClick = {
                    requestInFlight = true
                    statusText = "Requesting credentials for: $webId"

                    coroutineScope.launch {
                        try {
                            val rawJson = withContext(Dispatchers.IO) {
                                credentialClient.requestCredentialsJson(webId)
                            }

                            val obj = JSONObject(rawJson)
                            val status = obj.optString("status")

                            if (status != "granted") {
                                statusText = "Not granted: ${obj.optString("message", rawJson)}"
                                requestInFlight = false
                                return@launch
                            }

                            val grantedWebId = obj.optString("webId", webId)
                            val accessToken = obj.optString("accessToken")
                            val refreshToken = if (obj.isNull("refreshToken")) "" else obj.optString("refreshToken").trim()
                            val cleanedRefreshToken = refreshToken.takeIf { it.isNotBlank() && it.lowercase() != "null" } ?: ""
                            val expiresAtSeconds = obj.optLong("expiresAt", 0L)
                            val expiresAtMs = if (expiresAtSeconds > 0L) expiresAtSeconds * 1000L else 0L
                            val signingKey = obj.optString("signingKey")

                            tokenStore.setWebId(grantedWebId)
                            tokenStore.setAccessToken(accessToken)
                            tokenStore.setRefreshToken(cleanedRefreshToken)
                            tokenStore.setTokenExpiresAt(expiresAtMs)
                            tokenStore.setSigner(signingKey)

                            Log.d("Authorization", "Stored credentials for $grantedWebId, navigating…")
                            onLoginSuccess()
                        } catch (e: Throwable) {
                            statusText = "Failed: ${e.message}"
                        } finally {
                            requestInFlight = false
                        }
                    }
                }
            ) {
                Text(webId)
            }
        }
    }
}