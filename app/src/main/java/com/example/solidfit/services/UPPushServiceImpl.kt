package com.example.solidfit.services

import android.util.Log
import com.example.solidfit.data.AuthTokenStore
import com.example.solidfit.notifications.showNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okio.internal.commonToUtf8String
import org.json.JSONObject
import org.unifiedpush.android.connector.FailedReason
import org.unifiedpush.android.connector.PushService
import org.unifiedpush.android.connector.data.PushEndpoint
import org.unifiedpush.android.connector.data.PushMessage

enum class Actions {
    MESSAGE,
    FAILED,
    UNREGISTERED
}

private const val TAG = "UPPushServiceImpl"

class UPPushServiceImpl : PushService() {
    private var currEndpoint = ""
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessage(message: PushMessage, instance: String) {
        val msg = message.content.commonToUtf8String()
        Log.d(TAG, "new message received with content: $msg and content is ${if (message.decrypted) "decrypted" else "encrypted"}")
        this.applicationContext.broadcastPushMessageInfo(msg)

        var notificationText = msg
        try {
            val jsonMessage = JSONObject(msg)
            val generatedText = jsonMessage.optString("generated_text", "")
            if (generatedText.isNotBlank()) {
                notificationText = generatedText
                val store = AuthTokenStore(this.applicationContext)
                serviceScope.launch {
                    store.setSummaryText(generatedText)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Message is not JSON or missing generated_text, storing raw message")
            val store = AuthTokenStore(this.applicationContext)
            serviceScope.launch {
                store.setSummaryText(msg)
            }
        }

        showNotification(
            this.applicationContext,
            "SolidFit Push Notification",
            notificationText,
            "push_notification_channel"
        )
    }

    override fun onNewEndpoint(endpoint: PushEndpoint, instance: String) {
        Log.d(TAG, "new endpoint found at ${endpoint.url}")
        val newEndpoint = if (endpoint.url.contains("?up=1")) {
            endpoint.url.substring(0, endpoint.url.length - 5)
        } else {
            endpoint.url
        }
        this.currEndpoint = newEndpoint
        this.applicationContext.updatePushEndpoint(newEndpoint)
    }

    override fun onRegistrationFailed(reason: FailedReason, instance: String) {
        Log.d(TAG, "registration failed for instance $instance with reason $reason")
//        this.applicationContext.broadcastPushMessageInfo(Actions.FAILED.name, "2", reason.toString())
    }

    override fun onUnregistered(instance: String) {
        Log.d(TAG, "unregistered instance $instance")
//        this.applicationContext.broadcastPushMessageInfo(Actions.UNREGISTERED.name, "3", "unregistered ${this.currEndpoint}")
        this.currEndpoint = ""
    }
}
