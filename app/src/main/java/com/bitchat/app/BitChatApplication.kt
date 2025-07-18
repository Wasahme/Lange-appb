package com.bitchat.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class BitChatApplication : Application() {

    companion object {
        const val NOTIFICATION_CHANNEL_MESSAGES = "messages_channel"
        const val NOTIFICATION_CHANNEL_CALLS = "calls_channel"
        const val NOTIFICATION_CHANNEL_MESH = "mesh_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            
            // Messages channel
            val messagesChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_MESSAGES,
                "الرسائل",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات الرسائل الجديدة"
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Calls channel
            val callsChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_CALLS,
                "المكالمات",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "إشعارات المكالمات الواردة"
                enableVibration(true)
                setShowBadge(true)
            }
            
            // Mesh network channel
            val meshChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_MESH,
                "الشبكة الشبكية",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "حالة الشبكة الشبكية والاتصالات"
                enableVibration(false)
                setShowBadge(false)
            }
            
            notificationManager.createNotificationChannels(
                listOf(messagesChannel, callsChannel, meshChannel)
            )
        }
    }
}