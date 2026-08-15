package com.example.data.repository.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.data.local.PlannerDatabase
import com.example.data.local.notifications.NotificationDao
import com.example.data.local.notifications.NotificationEntity
import com.example.data.supabase.SupabaseService
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import com.example.data.models.StringOrNumericSerializer

@Serializable
data class SupabaseNotification(
    @Serializable(with = StringOrNumericSerializer::class)
    val id: String,
    val title: String,
    val description: String,
    val image_url: String? = null,
    val category: String = "General",
    val deep_link: String? = null,
    val created_at: String,
    val priority: String = "Normal", // Normal, High
    val target_type: String = "All", // All, Class, User
    val target_value: String? = null, // Class Name or User ID
    val action_button_text: String? = null,
    val scheduled_at: String? = null
)

class NotificationRepository(private val context: Context) {
    private val notificationDao: NotificationDao = PlannerDatabase.getDatabase(context).notificationDao()
    private val supabase = SupabaseService.client

    fun getAllNotifications(): Flow<List<NotificationEntity>> = notificationDao.getAllNotifications()

    fun getUnreadCount(): Flow<Int> = notificationDao.getUnreadCount()

    suspend fun syncNotifications() = withContext(Dispatchers.IO) {
        try {
            val remoteNotifications = supabase.from("notifications").select().decodeList<SupabaseNotification>()
            val remoteIds = remoteNotifications.map { it.id }.toSet()
            
            val localNotifications = notificationDao.getNotificationsList()
            val localMap = localNotifications.associateBy { it.id }

            // Delete local ones that are no longer on Supabase
            localNotifications.forEach { local ->
                if (!remoteIds.contains(local.id)) {
                    notificationDao.deleteNotification(local.id)
                }
            }

            // Insert or update remote ones
            remoteNotifications.forEach { remote ->
                val local = localMap[remote.id]
                val entity = NotificationEntity(
                    id = remote.id,
                    title = remote.title,
                    description = remote.description,
                    imageUrl = remote.image_url,
                    category = remote.category,
                    deepLink = remote.deep_link,
                    timestamp = local?.timestamp ?: System.currentTimeMillis(),
                    isRead = local?.isRead ?: false,
                    priority = remote.priority,
                    actionButtonText = remote.action_button_text
                )
                notificationDao.insertNotification(entity)
                
                // If it's a new notification, trigger local push
                if (local == null) {
                    sendLocalNotification(remote.title, remote.description, remote.id.hashCode(), remote.category)
                }
            }
        } catch (e: Exception) {
            Log.e("NotificationRepo", "Failed to sync notifications", e)
        }
    }

    private fun sendLocalNotification(title: String, message: String, id: Int, category: String = "") {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        val channelId = when (category.lowercase().trim()) {
            "books", "new books", "new_books" -> "new_books"
            "videos", "new videos", "new_videos" -> "new_videos"
            "tools", "new tools", "new_tools" -> "new_tools"
            "updates", "app updates", "app_updates" -> "app_updates"
            "announcements", "announcement" -> "announcements"
            "system" -> "system"
            else -> "announcements" // default fallback
        }

        val mainIntent = Intent(context, com.example.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            id,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(id, notification)
    }

    suspend fun markAsRead(id: String) = notificationDao.markAsRead(id)

    suspend fun markAllAsRead() = notificationDao.markAllAsRead()

    suspend fun deleteNotification(id: String) = notificationDao.deleteNotification(id)

    fun sendTestNotification(title: String, message: String) {
        sendLocalNotification(title, message, System.currentTimeMillis().toInt(), "system")
    }

    suspend fun saveNotificationLocally(notification: NotificationEntity) {
        notificationDao.insertNotification(notification)
    }

    suspend fun deleteAllNotifications() = notificationDao.deleteAllNotifications()
}
