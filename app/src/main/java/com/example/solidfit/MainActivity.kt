package com.example.solidfit

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_CONNECT
import android.Manifest.permission.BLUETOOTH_SCAN
import android.Manifest.permission.POST_NOTIFICATIONS
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.Manifest.permission.READ_MEDIA_IMAGES
import android.Manifest.permission.READ_MEDIA_VIDEO
import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import com.example.solidfit.ui.theme.WorkoutSolidProjectTheme

class MainActivity : ComponentActivity() {
    private lateinit var permLauncher: ActivityResultLauncher<Array<String>>

    @SuppressLint("InlinedApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permBatches = mutableListOf<Array<String>>().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(arrayOf(POST_NOTIFICATIONS))
                add(arrayOf(
                    READ_MEDIA_IMAGES,
                    READ_MEDIA_VIDEO,
                    READ_MEDIA_VISUAL_USER_SELECTED
                ))
            }
            add(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    arrayOf(BLUETOOTH_SCAN, BLUETOOTH_CONNECT)
                } else {
                    arrayOf(ACCESS_FINE_LOCATION, READ_EXTERNAL_STORAGE)
                }
            )
        }

        permLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { _ ->
            currentBatchIndex++
            if (currentBatchIndex < permBatches.size) {
                permLauncher.launch(permBatches[currentBatchIndex])
            }
        }

        currentBatchIndex = 0
        if (permBatches.isNotEmpty()) {
            permLauncher.launch(permBatches[0])
        }


        // Used to connect health connect object throughout the app
        val healthConnectManager = (application as WorkoutItemSolidApplication).healthConnectManager

        // Allows content to display behind device's status and navigation bar
        enableEdgeToEdge()
        setContent {
            WorkoutSolidProjectTheme {
                Authentication(healthConnectManager = healthConnectManager)
            }
        }
    }

    private var currentBatchIndex = 0
}