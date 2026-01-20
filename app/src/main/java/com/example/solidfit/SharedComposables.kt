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
import androidx.compose.runtime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext


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

    // prevents multiple refresh calls if this recomposes quickly
    val refreshMutex = remember { Mutex() }

    suspend fun forceLogoutToStart() {
        tokenStore.clearAuth()
        navController.navigate(SolidAuthFlowScreen.StartAuthScreen.name) {
            popUpTo(navController.graph.startDestinationId) { inclusive = true }
            launchSingleTop = true
        }
    }

    LaunchedEffect(expiresAt) {
        val exp = expiresAt ?: return@LaunchedEffect

        val now = System.currentTimeMillis()
        val skew = 60_000L

        // If already expired/near-expired: attempt refresh first
        if (exp <= 0L || exp <= now + skew) {
            val refreshed = refreshMutex.withLock {
                withContext(Dispatchers.IO) { tryRefreshTokens(tokenStore) }
            }
            if (!refreshed) forceLogoutToStart()
            return@LaunchedEffect
        }

        // Wait until it's near expiry
        val remaining = exp - now - skew
        if (remaining > 0) kotlinx.coroutines.delay(remaining)

        // When near expiry: refresh
        val refreshed = refreshMutex.withLock {
            withContext(Dispatchers.IO) { tryRefreshTokens(tokenStore) }
        }
        if (!refreshed) forceLogoutToStart()
    }

    content()
}
