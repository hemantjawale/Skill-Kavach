package com.example.skilkavach

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SafetyMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val repo = (application as SafetyApplication).repository
            repo.initialize()
            repo.saveDeviceToken(token)
            repo.scheduleSync()
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val repo = (application as SafetyApplication).repository
        repo.scheduleSync()
        if (
            Build.VERSION.SDK_INT >= 33 &&
                checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
        )
            return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                "safety-updates",
                "Safety updates",
                NotificationManager.IMPORTANCE_HIGH,
            )
        )
        val intent =
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat.Builder(this, "safety-updates")
                .setSmallIcon(R.drawable.ic_safety_notification)
                .setContentTitle("SurakshaSetu update")
                .setContentText("Open the app to review your safety and work updates.")
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setContentIntent(intent)
                .setAutoCancel(true)
                .build()
        manager.notify(message.data["notificationId"]?.hashCode() ?: 1, notification)
    }
}
