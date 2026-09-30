package com.example.solidfit.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.solidfit.R
import com.example.solidfit.healthdata.HealthConnectAvailability
import com.example.solidfit.healthdata.HealthConnectManager
import com.example.solidfit.healthdata.InputReadingsViewModel
import com.example.solidfit.healthdata.InputReadingsViewModelFactory

@RequiresApi(Build.VERSION_CODES.S)
@Composable
fun HeartRateMonitor(
    healthConnectManager: HealthConnectManager,
    permissions: Set<String>,
    uiState: InputReadingsViewModel.UiState,
    onInsertClick: (Double) -> Unit = {},
    onError: (Throwable?) -> Unit = {},
    onPermissionsResult: () -> Unit = {},
    onPermissionsLaunch: (Set<String>) -> Unit = {},
) {
    // TODO: Might be able to remove the permissionsGranted val above
    val activity = LocalContext.current as ComponentActivity
    val context = LocalContext.current
    val vm: InputReadingsViewModel = viewModel(
        activity,
        factory = InputReadingsViewModelFactory(
            healthConnectManager,
            activity
        )
    )
    val devices by remember { derivedStateOf {vm.devices}}
    val currentBpm by remember { derivedStateOf {vm.currentBpm}}
    val uiState by remember { derivedStateOf { vm.uiState }}

    // Permissions for finding and connecting to bluetooth heart rate monitor
    val scanPerm = Manifest.permission.BLUETOOTH_SCAN
    val connectPerm = Manifest.permission.BLUETOOTH_CONNECT

    val locationPerm = Manifest.permission.ACCESS_FINE_LOCATION

    LaunchedEffect(Unit) {
        healthConnectManager.checkAvailability()
    }

    val availability by remember { derivedStateOf { healthConnectManager.availability.value}}

    if (availability == HealthConnectAvailability.NOT_INSTALLED) {
        LaunchedEffect(Unit) {
            val uri = Uri.parse("market://details?id=com.google.android.apps.healthdata")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata")
                    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)}
                )
            }
        }
        // Optionally show a message while they install
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Health Connect is required: \nredirecting to Play Store…",
                textAlign = TextAlign.Center
            )
        }
        return
    }

    val healthLauncher = rememberLauncherForActivityResult(
        contract = vm.permissionsLauncher
    ) { grantedPermissions: Set<String> ->
        vm.initialLoad()
    }

    // Attempts to retrieve relevant bluetooth connections
    val bluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { }

    val scanGranted by remember {
        derivedStateOf {
            ContextCompat.checkSelfPermission(context, scanPerm) == PackageManager.PERMISSION_GRANTED
        }
    }
    val connectGranted by remember {
        derivedStateOf {
            ContextCompat.checkSelfPermission(context, connectPerm) == PackageManager.PERMISSION_GRANTED
        }
    }

    val locationGranted by remember {
        derivedStateOf {
            ContextCompat.checkSelfPermission(context, locationPerm) == PackageManager.PERMISSION_GRANTED
        }
    }

    val blePermissionsGranted = scanGranted && connectGranted
    val healthPermissionsGranted = vm.permissionsGranted.value

    LaunchedEffect(Unit) {
        if (!blePermissionsGranted || !locationGranted) {
            bluetoothLauncher.launch(
                arrayOf(
                    scanPerm,
                    connectPerm
                )
            )
        }
    }
    LaunchedEffect(uiState) {
        when (uiState) {
            is InputReadingsViewModel.UiState.Uninitialized -> {
                vm.initialLoad()
            }
            is InputReadingsViewModel.UiState.Error -> {
                onError((uiState as InputReadingsViewModel.UiState.Error).exception)
            }
            else -> { /* UiState.Done — nothing to do */ }
        }
    }

    LaunchedEffect(blePermissionsGranted, locationGranted, healthPermissionsGranted) {
        if ((blePermissionsGranted && healthPermissionsGranted) || (locationGranted && healthPermissionsGranted)) {
            vm.startBleScan()
        }
    }

    if (uiState != InputReadingsViewModel.UiState.Uninitialized) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!blePermissionsGranted && !locationGranted) {
                item {
                    Text("Waiting for Bluetooth permissions...", fontSize = 20.sp)
                }
                return@LazyColumn
            }
            if (!healthPermissionsGranted) {
                item {
                    Button(
                        onClick = { healthLauncher.launch(vm.permissions) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.hsl(224f, 1f,0.73f)
                        )
                    ) {
                        Text(text = stringResource(R.string.permissions_button_label))
                    }
                }
                return@LazyColumn
            }
            else {
                // If we have no bpm yet…
                if (currentBpm == null) {
                    if (devices.isEmpty()) {
                        item {
                            Text("Searching for devices…", fontSize = 20.sp)
                        }
                    } else {
                        // Show a row for each found device
                        items(devices) { device ->
                            Button(
                                onClick = { vm.connectTo(device) },
                                modifier = Modifier
                                    .padding(vertical = 4.dp),
                                colors = ButtonColors(
                                    Color.hsl(
                                    224f,
                                    1f,
                                    0.73f
                                    ),
                                    Color.Black,
                                    Color.hsl(
                                    224f,
                                    1f,
                                    0.73f
                                    ),
                                    Color.Black
                                )
                            ) {
                                Text(text = device.name ?: device.address)
                            }
                        }
                    }
                } else {
                    // Connected & have BPM
                    item {
                        Text(
                            text = "Current heart rate",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "$currentBpm BPM",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}







