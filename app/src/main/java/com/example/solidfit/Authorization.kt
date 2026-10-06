package com.example.solidfit

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import com.example.solidfit.data.AuthTokenStore
import com.example.solidfit.healthdata.HealthConnectManager
import com.example.solidfit.screens.AuthCompleteScreen
import com.example.solidfit.screens.ScmSignInScreen
import com.example.solidfit.screens.StartAuthScreen
import com.example.solidfit.screens.UnfetchableWebIdScreen
import com.example.solidfit.screens.UpdateWorkouts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    SummaryScreen,
    ScmSignInScreen,
}


// Used for navbar
sealed class BottomNavItem(val route: String, val title: String, val icon: ImageVector) {
    data object WorkoutList : BottomNavItem(
        route = SolidAuthFlowScreen.WorkoutList.name,
        title = "Workout List",
        icon = Icons.AutoMirrored.Filled.List
    )
    data object Summary: BottomNavItem(
        route = SolidAuthFlowScreen.SummaryScreen.name,
        title = "Summary",
        icon = Icons.Default.Star
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
    val coroutineScope = rememberCoroutineScope()

    NavHost(
        navController = navController,
        startDestination = SolidAuthFlowScreen.LandingScreen.name
    ) {
        // SCREEN: Landing Screen
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
                    navController.navigate(SolidAuthFlowScreen.ScmSignInScreen.name) {
                        popUpTo(SolidAuthFlowScreen.LandingScreen.name) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // SCREEN: Sign in through the Solid Credential Manager (browser only when it's unavailable)
        composable(SolidAuthFlowScreen.ScmSignInScreen.name) {
            ScmSignInScreen(
                tokenStore = tokenStore,
                onSignedIn = {
                    navController.navigate(SolidAuthFlowScreen.UpdateWorkouts.name) {
                        popUpTo(SolidAuthFlowScreen.ScmSignInScreen.name) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onUseBrowser = {
                    navController.navigate(SolidAuthFlowScreen.StartAuthScreen.name) {
                        popUpTo(SolidAuthFlowScreen.ScmSignInScreen.name) { inclusive = true }
                        launchSingleTop = true
                    }
                },
            )
        }

        // SCREEN: Start Authentication
        composable(route = SolidAuthFlowScreen.StartAuthScreen.name) {
            StartAuthScreen(
                tokenStore = tokenStore,
                onFailNavigation = {
                    coroutineScope.launch {
                        withContext(Dispatchers.Main) {
                            navController.navigate(SolidAuthFlowScreen.UnfetchableWebIdScreen.name)
                        }
                    }
                },
                onInvalidInput = { msg ->
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(context, msg.toString(), Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }

        // SCREEN: UnfetchableWebID
        composable(route = SolidAuthFlowScreen.UnfetchableWebIdScreen.name) {
            UnfetchableWebIdScreen(tokenStore = tokenStore) { err ->
                Handler(Looper.getMainLooper()).post {
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                }
            }
        }

        // SCREEN: Authentication complete
        composable(
            route = SolidAuthFlowScreen.AuthCompleteScreen.name,
            deepLinks = listOf(navDeepLink { uriPattern = "com.example.solidfit://auth/callback" })
        ) {
            AuthCompleteScreen(
                tokenStore = tokenStore,
                onFinishedAuth = {
                    coroutineScope.launch { tokenStore.setAuthSource(AuthTokenStore.AUTH_SOURCE_BROWSER) }
                    navController.navigate(SolidAuthFlowScreen.UpdateWorkouts.name) {
                        popUpTo(SolidAuthFlowScreen.AuthCompleteScreen.name) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onFailedAuth = {
                    Toast.makeText(context, "Sign-in failed. Please try again.", Toast.LENGTH_SHORT).show()
                    navController.navigate(SolidAuthFlowScreen.StartAuthScreen.name) {
                        popUpTo(SolidAuthFlowScreen.AuthCompleteScreen.name) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }

        // SCREEN: UpdateWorkouts
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
        val skew = refreshLeadMs(tokenStore)

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
            withContext(Dispatchers.Main) {
                onValidToken()
            }
            return@LaunchedEffect
        }

        // If not valid, try refresh before forcing login
        val refreshToken = tokenStore.getRefreshToken().first()
        val isScmSession = tokenStore.getAuthSource().first() == AuthTokenStore.AUTH_SOURCE_SCM
        val canRefresh = isScmSession || (refreshToken.isNotBlank() && refreshToken != "null")

        if (canRefresh) {
            val refreshed = tryRefreshTokens(tokenStore)
            if (refreshed != null && looksValid()) {
                withContext(Dispatchers.Main) {
                    onValidToken()
                }
                return@LaunchedEffect
            }
        }

        withContext(Dispatchers.Main) {
            onNeedsLogin()
        }
    }
}
