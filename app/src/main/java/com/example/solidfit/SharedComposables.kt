package com.example.solidfit

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.example.solidfit.data.AuthTokenStore


@Composable
fun StartButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color.hsl(224f, 1f,0.73f))) {
        Text(text = text, fontSize = 16.sp)
    }
}

@Composable
fun RequireValidAuthToken(
    tokenStore: AuthTokenStore,
    navController: NavController,
    content: @Composable () -> Unit
) {
    val expiresAt: Long? by tokenStore
        .getTokenExpiresAt()
        .collectAsState(initial = null)

    LaunchedEffect(expiresAt) {
        val exp = expiresAt ?: return@LaunchedEffect

        val now = System.currentTimeMillis()
        val skew = 60_000L

        val isExpired = exp <= 0L || exp <= (now + skew)
        if (isExpired) {
            tokenStore.clearAuth()
            navController.navigate(SolidAuthFlowScreen.StartAuthScreen.name) {
                // safer than popUpTo(UpdateWorkouts) because you may not be on that route
                popUpTo(navController.graph.startDestinationId) { inclusive = true }
                launchSingleTop = true
            }
            return@LaunchedEffect
        }

        val remaining = exp - now - skew
        if (remaining > 0) {
            kotlinx.coroutines.delay(remaining)
        }

        tokenStore.clearAuth()
        navController.navigate(SolidAuthFlowScreen.StartAuthScreen.name) {
            popUpTo(navController.graph.startDestinationId) { inclusive = true }
            launchSingleTop = true
        }
    }

    content()
}