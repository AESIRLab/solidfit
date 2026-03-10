package com.example.solidfit.services

import android.content.Context
import android.content.Intent

fun Context.broadcastPushMessageInfo(
    response: String
) {
    val broadcastIntent = Intent()
    broadcastIntent.`package` = this.packageName
    broadcastIntent.action = "RESPONSE"
    broadcastIntent.putExtra("response", response)
    this.sendBroadcast(broadcastIntent)
}

fun Context.updatePushEndpoint(endpoint: String) {
    val broadcastIntent = Intent()
    broadcastIntent.`package` = this.packageName
    broadcastIntent.action = "UPDATE_PUSH_ENDPOINT"
    broadcastIntent.putExtra("endpoint", endpoint)
    this.sendBroadcast(broadcastIntent)
}
