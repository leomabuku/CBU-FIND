package com.campus.lostandfound.data.remote

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.campus.lostandfound.MainActivity
import com.campus.lostandfound.R
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONObject

class CbuFindMessagingService : FirebaseMessagingService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val app = application as com.campus.lostandfound.CampusLostAndFoundApp
        scope.launch { runCatching { app.container.workerApi.request("/v1/devices", "PUT", JSONObject().put("token", token).put("platform", "ANDROID")) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "CBU Find updates", NotificationManager.IMPORTANCE_HIGH))
        }
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).apply {
            message.data["conversationId"]?.takeIf(String::isNotBlank)?.let { putExtra(MainActivity.EXTRA_CONVERSATION_ID, it) }
            message.data["claimId"]?.takeIf(String::isNotBlank)?.let { putExtra(MainActivity.EXTRA_CLAIM_ID, it) }
            message.data["itemId"]?.takeIf(String::isNotBlank)?.let { putExtra(MainActivity.EXTRA_ITEM_ID, it) }
        }
        val requestCode = message.messageId?.hashCode() ?: message.data.hashCode()
        val pendingIntent = PendingIntent.getActivity(this, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.cbu_find_logo)
            .setContentTitle(message.notification?.title ?: "CBU Find")
            .setContentText(message.notification?.body ?: "Open CBU Find for an update.")
            .setAutoCancel(true).setContentIntent(pendingIntent).build()
        manager.notify(message.messageId?.hashCode() ?: System.currentTimeMillis().toInt(), notification)
    }

    private companion object { const val CHANNEL_ID = "cbu_find_updates" }
}
