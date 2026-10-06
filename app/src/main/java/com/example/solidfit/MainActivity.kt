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
import android.content.ComponentName
import android.content.ContentValues.TAG
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.example.solidfit.data.generateGetRequest
import com.example.solidfit.ui.theme.WorkoutSolidProjectTheme
import com.nimbusds.jose.JOSEObjectType
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.JWSHeader
import com.nimbusds.jose.crypto.ECDSASigner
import com.nimbusds.jose.jwk.ECKey
import com.nimbusds.jose.jwk.JWK
import com.nimbusds.jwt.JWTClaimsSet
import com.nimbusds.jwt.SignedJWT
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import java.time.Instant
import java.util.Calendar
import java.util.UUID
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import com.example.solidfit.data.AuthTokenStore
import com.example.solidfit.data.RecentWebIdStore

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
                Authorization(healthConnectManager = healthConnectManager)
            }
        }
    }

    private var currentBatchIndex = 0
}

private fun generateCustomToken(signingJwk: String, method: String, uri: String): String {
    val parsedKey = ECKey.parse(JWK.parse(signingJwk).toJSONObject())
    val ecPublicJWK = parsedKey.toPublicJWK()

    val signer = ECDSASigner(parsedKey)

    val body = JWTClaimsSet.Builder()
        .claim("htu", uri)
        .claim("htm", method)
        .issueTime(Calendar.getInstance().time)
        .jwtID(UUID.randomUUID().toString())
        .build()

    val header = JWSHeader.Builder(JWSAlgorithm.ES256)
        .type(JOSEObjectType("dpop+jwt"))
        .jwk(ecPublicJWK)
        .build()

    val signedJWT = SignedJWT(
        header,
        body
    )
    signedJWT.sign(signer)
    return signedJWT.serialize()
}