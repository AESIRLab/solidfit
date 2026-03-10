package com.example.solidfit.services

import android.util.Log
import com.example.solidfit.notifications.showNotification
import okio.internal.commonToUtf8String
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

    override fun onMessage(message: PushMessage, instance: String) {
        Log.d(TAG, "new message received with content: ${message.content.commonToUtf8String()} and content is ${if (message.decrypted) "decrypted" else "encrypted"}")
//        this.applicationContext.broadcastPushMessageInfo(Actions.MESSAGE.name, "1", message.content.toString())

        showNotification(
            this.applicationContext,
            "SolidFit Push Notification",
            message.content.commonToUtf8String(),
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
        this.applicationContext.broadcastPushMessageInfo(Actions.FAILED.name, "2", reason.toString())
    }

    override fun onUnregistered(instance: String) {
        Log.d(TAG, "unregistered instance $instance")
        this.applicationContext.broadcastPushMessageInfo(Actions.UNREGISTERED.name, "3", "unregistered ${this.currEndpoint}")
        this.currEndpoint = ""
    }
}
