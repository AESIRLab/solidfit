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
import androidx.compose.runtime.collectAsState
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

    val webId by tokenStore.getWebId().collectAsState(initial = "")
    val isSignedIn = webId.isNotBlank()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

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
        Button(onClick = {
            coroutineScope.launch(Dispatchers.IO) {
                tokenStore.clearAuth() // if you have this helper, use it
            }
            Toast.makeText(context, "Signed out", Toast.LENGTH_SHORT).show()

            navController.navigate(SolidAuthFlowScreen.CredentialManagerAuthScreen.name) {
                popUpTo(navController.graph.findStartDestination().id) { inclusive = true }
                launchSingleTop = true
            }
        }) {
            Text("Sign out")
        }
    }
}