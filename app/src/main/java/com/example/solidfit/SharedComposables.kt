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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext


@Composable
fun StartButton(text: String, onClick: () -> Unit) {
    Button(onClick = onClick, colors = ButtonDefaults.buttonColors(containerColor = Color.hsl(224f, 1f,0.73f))) {
        Text(text = text, fontSize = 16.sp)
    }
}

/**
 * How long before expiry to get a new token: a minute for browser sessions; for SCM sessions
 * min(2 min, a sixth of the token's lifetime), which stays under the SCM's own refresh threshold.
 */
suspend fun refreshLeadMs(tokenStore: AuthTokenStore): Long {
    if (tokenStore.getAuthSource().first() != AuthTokenStore.AUTH_SOURCE_SCM) return 60_000L
    val lifetime = (tokenStore.getTokenExpiresAt().first() - tokenStore.getTokenReceivedAt().first()).coerceAtLeast(0L)
    return minOf(2 * 60_000L, lifetime / 6)
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
    suspend fun forceLogoutToStart(
        tokenStore: AuthTokenStore,
        navController: NavController
    ) {
        tokenStore.clearAuth() // or whatever you do to invalidate the token

        withContext(Dispatchers.Main) {
            navController.navigate(SolidAuthFlowScreen.LandingScreen.name) {
                popUpTo(SolidAuthFlowScreen.LandingScreen.name) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    LaunchedEffect(expiresAt) {
        val exp = expiresAt ?: return@LaunchedEffect

        val now = System.currentTimeMillis()
        val skew = refreshLeadMs(tokenStore)

        if (exp <= 0L) return@LaunchedEffect

        if (exp <= now + skew) {
            val refreshed = refreshMutex.withLock {
                withContext(Dispatchers.IO) { tryRefreshTokens(tokenStore) }
            }
            if (refreshed == null) forceLogoutToStart(tokenStore, navController)
            return@LaunchedEffect
        }

        // Wait until it's near expiry
        val remaining = exp - now - skew
        if (remaining > 0) kotlinx.coroutines.delay(remaining)

        // When near expiry: refresh
        val refreshed = refreshMutex.withLock {
            withContext(Dispatchers.IO) { tryRefreshTokens(tokenStore) }
        }
        if (refreshed == null) forceLogoutToStart(navController = navController, tokenStore = tokenStore)
    }

    content()
}
