package com.example.solidfit.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.example.solidfit.SolidAuthFlowScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.example.solidfit.data.AuthTokenStore

@Composable
fun SettingsScreen(
    navController: NavHostController
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    // Get the token store, just as you do in other screens
    val tokenStore = remember { AuthTokenStore(context.applicationContext) }

    // Create a state variable to hold the WebID
    var webId by remember { mutableStateOf<String?>(null) }

    // Fetch the WebID from the DataStore when the screen first loads
    LaunchedEffect(Unit) {
        webId = tokenStore.getWebId().firstOrNull()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        val isSignedIn = !webId.isNullOrBlank()

        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(fontSize = 19.sp, fontWeight = FontWeight.Medium)) {
                    append(if (isSignedIn) "Signed in as:" else "Not Signed in")
                }
            },
        )

        if (isSignedIn) {
            Text(
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp),
                text = buildAnnotatedString {
                    withStyle(style = ParagraphStyle(lineHeight = 30.sp)) {
                        withStyle(
                            style = SpanStyle(
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Normal
                            )
                        ) {
                            append("\t\t\t$webId")
                        }
                    }
                }
            )
        }

        // Sign Out Button
        Button(
            onClick = {
                // Launch a coroutine to clear the DataStore
                coroutineScope.launch(Dispatchers.IO) {
                    // Clear all stored authentication data
                    tokenStore.setAccessToken("")
                    tokenStore.setIdToken("")
                    tokenStore.setRefreshToken("")
                    tokenStore.setWebId("")
                    tokenStore.setSigner("")
                    tokenStore.setClientId("")
                    tokenStore.setClientSecret("")
                    tokenStore.setCodeVerifier("")
                    tokenStore.setTokenUri("")
                    tokenStore.setOidcProvider("")
                    tokenStore.setRedirectUri("")
                }

                // Show toast
                Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()

                // Navigate back to the very start and clear the back stack
                navController.navigate(SolidAuthFlowScreen.StartAuthScreen.name) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        inclusive = true // Clears the entire app stack
                    }
                    launchSingleTop = true // Avoids multiple copies of the login screen
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.hsl(224f, 1f, 0.73f, 0.75f)
            )
        ) {
            Text("Sign out")
        }
    }
}