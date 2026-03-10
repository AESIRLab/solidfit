package com.example.solidfit.screens

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.solidfit.services.UPPushServiceImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import com.example.solidfit.data.AuthTokenStore
import org.unifiedpush.android.connector.UnifiedPush

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

    // UnifiedPush state
    var distribSelected by rememberSaveable { mutableStateOf(false) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    var serviceStarted by rememberSaveable { mutableStateOf(false) }
    val mBound = remember { mutableStateOf(false) }

    val mConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                mBound.value = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                mBound.value = false
            }
        }
    }

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

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // UnifiedPush section
        Text(
            text = "Unified Push Notifications",
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(12.dp))

        if (!serviceStarted) {
            Button(onClick = {
                Intent(context, UPPushServiceImpl::class.java).also { intent ->
                    context.applicationContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
                }
                serviceStarted = true
            }) {
                Text("Start Unified Push Service")
            }
        } else {
            Button(onClick = {
                val serviceIntent = Intent(context, UPPushServiceImpl::class.java)
                context.applicationContext.stopService(serviceIntent)
                serviceStarted = false
            }) {
                Text("Stop Unified Push")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (!distribSelected) {
            Button(onClick = { expanded = true }) {
                Text("Select Distributor")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Text("Select a distributor service:", modifier = Modifier.padding(8.dp))
                UnifiedPush.getDistributors(context.applicationContext).forEach { distributor ->
                    DropdownMenuItem(
                        text = { Text(text = distributor) },
                        onClick = {
                            UnifiedPush.saveDistributor(context.applicationContext, distributor)
                            UnifiedPush.register(context.applicationContext)
                            distribSelected = true
                            expanded = false
                        }
                    )
                }
            }
        } else {
            Button(onClick = {
                UnifiedPush.unregister(context.applicationContext)
                distribSelected = false
            }) {
                Text("Unregister Push Connection")
            }
        }
    }
}