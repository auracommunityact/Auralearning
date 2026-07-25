package com.example.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.local.PlannerDatabase
import com.example.data.local.notifications.NotificationEntity
import com.example.data.repository.notifications.SupabaseNotification
import com.example.data.supabase.SupabaseService
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import java.util.UUID

class RealtimeNotificationService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val supabase = SupabaseService.client
    private val notificationManager by lazy { getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager }

    companion object {
        private const val CHANNEL_ID = "realtime_notification_service"
        private const val NOTIFICATION_ID = 1001
        
        fun start(context: Context) {
            try {
                val intent = Intent(context, RealtimeNotificationService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("RealtimeService", "Failed to start service", e)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createServiceNotificationChannel()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, createServiceNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, createServiceNotification())
        }
        startListening()
    }

    private fun createServiceNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Notification Sync",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ensures you receive notifications instantly"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createServiceNotification(): Notification {
        val intent = Intent(this, Class.forName("com.example.MainActivity"))
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Aura Real-time Active")
            .setContentText("Listening for new updates...")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()
    }

    private fun startListening() {
        val channel = supabase.realtime.channel("notifications_realtime")
        
        channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
            table = "notifications"
        }.onEach { action ->
            val data = action.record
            try {
                // Decode the notification directly from JsonObject
                val json = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                val notification = json.decodeFromJsonElement<SupabaseNotification>(data)
                
                handleNewNotification(notification)
            } catch (e: Exception) {
                Log.e("RealtimeService", "Error decoding notification", e)
            }
        }.launchIn(serviceScope)

        serviceScope.launch {
            try {
                channel.subscribe()
                Log.d("RealtimeService", "Subscribed to notifications")
            } catch (e: Exception) {
                Log.e("RealtimeService", "Subscription failed", e)
            }
        }
    }

    private suspend fun handleNewNotification(notif: SupabaseNotification) {
        val db = PlannerDatabase.getDatabase(this)
        
        // Save locally
        val entity = NotificationEntity(
            id = notif.id,
            title = notif.title,
            description = notif.description,
            imageUrl = notif.image_url,
            category = notif.category,
            deepLink = notif.deep_link,
            timestamp = System.currentTimeMillis(),
            isRead = false,
            priority = notif.priority,
            actionButtonText = notif.action_button_text
        )
        db.notificationDao().insertNotification(entity)

        // Show system notification
        showPushNotification(notif)
        
        // Notify the app that data changed
        com.example.data.repository.AuraRepository.notifyNotificationsChanged()
    }

    private fun showPushNotification(notif: SupabaseNotification) {
        val channelId = getChannelForCategory(notif.category)
        
        val intent = Intent(this, Class.forName("com.example.MainActivity")).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("notification_id", notif.id)
            putExtra("deep_link", notif.deep_link)
        }
        
        val pendingIntent = PendingIntent.getActivity(
            this, notif.id.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(notif.title)
            .setContentText(notif.description)
            .setPriority(if (notif.priority == "High") NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)

        if (notif.priority == "High") {
            builder.setDefaults(Notification.DEFAULT_ALL)
        }

        notificationManager.notify(notif.id.hashCode(), builder.build())
    }

    private fun getChannelForCategory(category: String): String {
        return when (category.lowercase()) {
            "announcements" -> "announcements"
            "books" -> "new_books"
            "videos" -> "new_videos"
            "tools" -> "new_tools"
            "updates" -> "app_updates"
            else -> "announcements"
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}
