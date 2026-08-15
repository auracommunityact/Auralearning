package com.example.notifications

import android.content.Context
import com.example.data.local.PlannerDatabase
import com.example.data.local.notifications.NotificationEntity
import com.example.data.local.NotificationPreferences
import com.example.data.local.dataStore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

class MyFirebaseMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        
        // Handle the message
        val title = message.data["title"] ?: message.notification?.title
        val body = message.data["body"] ?: message.notification?.body
        
        if (title != null && body != null) {
            val notification = NotificationEntity(
                id = UUID.randomUUID().toString(),
                title = title,
                description = body,
                imageUrl = message.data["imageUrl"],
                category = message.data["category"] ?: "General",
                deepLink = message.data["deepLink"],
                timestamp = System.currentTimeMillis(),
                isRead = false
            )
            
            // Save to database
            CoroutineScope(Dispatchers.IO).launch {
                val db = PlannerDatabase.getDatabase(applicationContext)
                
                // Check settings (e.g., if category is enabled)
                val settings = applicationContext.dataStore.data.first()
                val category = message.data["category"] ?: "General"
                
                val enabled = when (category) {
                    "Books" -> settings[NotificationPreferences.BOOKS_ENABLED] ?: true
                    "Videos" -> settings[NotificationPreferences.VIDEOS_ENABLED] ?: true
                    "Resources" -> settings[NotificationPreferences.RESOURCES_ENABLED] ?: true
                    else -> true
                }

                if (enabled) {
                    db.notificationDao().insertNotification(notification)
                    com.example.utils.NotificationHelper.showNotification(
                        applicationContext,
                        notification.title,
                        notification.description,
                        category,
                        notification.deepLink
                    )
                }
            }
        }
    }
}
