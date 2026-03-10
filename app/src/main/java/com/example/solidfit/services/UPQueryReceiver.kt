package com.example.solidfit.services
//
//import android.content.Context
//import android.content.Intent
//import android.util.Log
//import kotlinx.coroutines.CoroutineScope
//import kotlinx.coroutines.Dispatchers
//import kotlinx.coroutines.SupervisorJob
//import kotlinx.coroutines.cancel
//import org.json.JSONObject
//import org.unifiedpush.android.connector.MessagingReceiver
//
//private const val TAG = "CustomReceiver"
//class UPQueryReceiver : MessagingReceiver() {
//    private lateinit var  serviceScope: CoroutineScope
//
//    override fun onReceive(context: Context, intent: Intent) {
//        super.onReceive(context, intent)
//        Log.d(TAG, "onReceive")
//    }
//
//    override fun onRegistrationFailed(context: Context, instance: String) {
//        super.onRegistrationFailed(context, instance)
//    }
//
//    override fun onUnregistered(context: Context, instance: String) {
//        super.onUnregistered(context, instance)
//        if (::serviceScope.isInitialized) {
//            serviceScope.cancel()
//        }
//    }
//
//    // TODO: something idk
//    override fun onMessage(context: Context, message: ByteArray, instance: String) {
//        super.onMessage(context, message, instance)
//        Log.d(TAG, "received new message!")
//        val msg = message.toString(Charsets.UTF_8)
//        val jsonMessage = JSONObject(msg)
//        val queryId = jsonMessage.getString("query_id")
//        val generatedText = jsonMessage.getString("generated_text")
//        Log.d(TAG, "message: ${message.toString(Charsets.UTF_8)}")
//
//        context.broadcastMessageInfo(queryId, generatedText)
//    }
//
//    override fun onNewEndpoint(context: Context, endpoint: String, instance: String) {
//        super.onNewEndpoint(context, endpoint, instance)
//        Log.d(TAG, "new endpoint found: $endpoint")
//        val newEndpoint = if (endpoint.contains("?up=1")) endpoint.substring(0, endpoint.length - 5) else endpoint
//        Log.d(TAG, "newEndpoint: $newEndpoint")
//        if (!::serviceScope.isInitialized) {
//            serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
//        }
//        context.updateRegistrationInfo(newEndpoint)
//    }
//}