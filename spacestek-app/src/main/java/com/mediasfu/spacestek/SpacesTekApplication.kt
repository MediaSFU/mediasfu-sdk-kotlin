package com.mediasfu.spacestek

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class SpacesTekApplication : Application() {
    
    companion object {
        const val SPACE_CHANNEL_ID = "spaces_channel"
    }
    
    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }
    
    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val spaceChannel = NotificationChannel(
                SPACE_CHANNEL_ID,
                getString(R.string.space_notification_channel),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification channel for active audio spaces"
                setShowBadge(true)
            }
            
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(spaceChannel)
        }
    }
}
