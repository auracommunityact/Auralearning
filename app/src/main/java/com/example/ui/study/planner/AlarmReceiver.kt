package com.example.ui.study.planner

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "com.example.EXAM_COUNTDOWN_TRIGGERED") {
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    val db = com.example.data.local.PlannerDatabase.getDatabase(context)
                    val exams = db.examDateSheetDao().getAllExams()
                    val currentTick = System.currentTimeMillis()
                    val nextExam = exams.firstOrNull { it.timestamp > currentTick }

                    if (nextExam != null) {
                        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            val channel = NotificationChannel(
                                "exam_alerts",
                                "Exam Reminders",
                                NotificationManager.IMPORTANCE_HIGH
                            ).apply {
                                description = "Daily reminders for upcoming exams"
                            }
                            notificationManager.createNotificationChannel(channel)
                        }

                        val mainIntent = Intent(context, com.example.MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        val pendingIntent = PendingIntent.getActivity(
                            context,
                            999,
                            mainIntent,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                        )

                        val diff = nextExam.timestamp - currentTick
                        val days = diff / (24 * 60 * 60 * 1000L)

                        val title = "📚 Exam is coming: ${nextExam.subject}!"
                        val message = if (days <= 0) {
                            "Your ${nextExam.subject} exam is today at ${nextExam.examTime}! Stay focused and best of luck!"
                        } else {
                            "Your ${nextExam.subject} exam is approaching in $days days (${nextExam.examDate} at ${nextExam.examTime}). Study hard!"
                        }

                        val notification = NotificationCompat.Builder(context, "exam_alerts")
                            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                            .setContentTitle(title)
                            .setContentText(message)
                            .setPriority(NotificationCompat.PRIORITY_HIGH)
                            .setContentIntent(pendingIntent)
                            .setAutoCancel(true)
                            .build()

                        notificationManager.notify(8888, notification)
                    }

                    // Reschedule for next day (24 hours later)
                    com.example.ui.study.scheduleDailyExamNotification(context)
                } catch (e: Exception) {
                    Log.e("AlarmReceiver", "Error sending exam reminder notification", e)
                }
            }
            return
        }

        if (intent.action == "com.example.ALARM_TRIGGERED") {
            val sessionId = intent.getLongExtra("SESSION_ID", -1)
            val subject = intent.getStringExtra("SUBJECT") ?: "Study Time"
            val topic = intent.getStringExtra("TOPIC") ?: "Let's focus"

            Log.d("AlarmReceiver", "Alarm triggered for session $sessionId")

            val alarmIntent = Intent(context, AlarmActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("SESSION_ID", sessionId)
                putExtra("SUBJECT", subject)
                putExtra("TOPIC", topic)
            }

            val pendingIntent = PendingIntent.getActivity(
                context,
                sessionId.toInt(),
                alarmIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "study_alarms",
                    "Study Alarms",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Alarms for scheduled study sessions"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val timeFormat = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            val timeStr = timeFormat.format(java.util.Date(intent.getLongExtra("TIME", System.currentTimeMillis())))

            val notification = NotificationCompat.Builder(context, "study_alarms")
                .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("📚 Time to Study!")
                .setContentText("$subject - $topic starts at $timeStr.")
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setFullScreenIntent(pendingIntent, true)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(sessionId.toInt(), notification)
        }
    }
}

