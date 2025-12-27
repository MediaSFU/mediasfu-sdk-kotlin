package com.mediasfu.spacestek

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class SpaceService : Service() {
    
    companion object {
        private const val NOTIFICATION_ID = 2001
        
        fun start(context: Context, spaceName: String) {
            val intent = Intent(context, SpaceService::class.java).apply {
                putExtra("space_name", spaceName)
            }
            context.startForegroundService(intent)
        }
        
        fun stop(context: Context) {
            context.stopService(Intent(context, SpaceService::class.java))
        }
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val spaceName = intent?.getStringExtra("space_name") ?: "Space"
        startForeground(NOTIFICATION_ID, createNotification(spaceName))
        return START_STICKY
    }
    
    private fun createNotification(spaceName: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, SpacesTekApplication.SPACE_CHANNEL_ID)
            .setContentTitle(getString(R.string.space_notification_title))
            .setContentText(spaceName)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
