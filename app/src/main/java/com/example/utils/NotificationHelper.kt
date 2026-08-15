package com.example.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build

object NotificationHelper {
    const val CHANNEL_STUDY_ALARMS = "study_alarms"
    const val CHANNEL_NEW_BOOKS = "new_books"
    const val CHANNEL_NEW_VIDEOS = "new_videos"
    const val CHANNEL_NEW_TOOLS = "new_tools"
    const val CHANNEL_APP_UPDATES = "app_updates"
    const val CHANNEL_ANNOUNCEMENTS = "announcements"
    const val CHANNEL_SYSTEM = "system"

    fun registerNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                
                val channels = listOf(
                    NotificationChannel(
                        CHANNEL_STUDY_ALARMS,
                        "Study Alarms",
                        NotificationManager.IMPORTANCE_HIGH
                    ).apply {
                        description = "Alarms for scheduled study sessions"
                        enableVibration(true)
                        setShowBadge(true)
                    },
                    NotificationChannel(
                        CHANNEL_NEW_BOOKS,
                        "New Books",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Notify when new books are added"
                        enableVibration(true)
                        setShowBadge(true)
                    },
                    NotificationChannel(
                        CHANNEL_NEW_VIDEOS,
                        "New Videos",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Notify when new videos are uploaded"
                        enableVibration(true)
                        setShowBadge(true)
                    },
                    NotificationChannel(
                        CHANNEL_NEW_TOOLS,
                        "New Tools",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Notify about new tools"
                        enableVibration(true)
                        setShowBadge(true)
                    },
                    NotificationChannel(
                        CHANNEL_APP_UPDATES,
                        "App Updates",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Notify about application updates"
                        enableVibration(true)
                        setShowBadge(true)
                    },
                    NotificationChannel(
                        CHANNEL_ANNOUNCEMENTS,
                        "Announcements",
                        NotificationManager.IMPORTANCE_DEFAULT
                    ).apply {
                        description = "Important announcements and news"
                        enableVibration(true)
                        setShowBadge(true)
                    },
                    NotificationChannel(
                        CHANNEL_SYSTEM,
                        "System",
                        NotificationManager.IMPORTANCE_LOW
                    ).apply {
                        description = "System notifications and status alerts"
                        enableVibration(true)
                        setShowBadge(true)
                    }
                )
                
                notificationManager.createNotificationChannels(channels)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun showNotification(
        context: Context,
        title: String,
        message: String,
        category: String = "General",
        deepLink: String? = null
    ) {
        val channelId = when (category.lowercase()) {
            "books" -> CHANNEL_NEW_BOOKS
            "videos" -> CHANNEL_NEW_VIDEOS
            "tools", "resources" -> CHANNEL_NEW_TOOLS
            "announcements" -> CHANNEL_ANNOUNCEMENTS
            "updates" -> CHANNEL_APP_UPDATES
            else -> CHANNEL_SYSTEM
        }

        val intent = android.content.Intent(context, com.example.MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            if (!deepLink.isNullOrBlank()) {
                putExtra("deep_link", deepLink)
            }
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_DEFAULT)

        val notificationManager = androidx.core.app.NotificationManagerCompat.from(context)
        try {
            notificationManager.notify(System.currentTimeMillis().toInt(), builder.build())
        } catch (e: SecurityException) {
            e.printStackTrace()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
