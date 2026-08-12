import re

file_path = "app/src/main/java/com/example/notifications/RealtimeNotificationService.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """    override fun onCreate() {
        super.onCreate()
        try {
            createServiceNotificationChannel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, createServiceNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, createServiceNotification())
            }
            startListening()
        } catch (e: Exception) {
            Log.e("RealtimeService", "Error starting foreground service or listening", e)
        }
    }"""

replacement = """    private var isListening = false

    override fun onCreate() {
        super.onCreate()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            createServiceNotificationChannel()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFICATION_ID, createServiceNotification(), android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, createServiceNotification())
            }
            if (!isListening) {
                startListening()
                isListening = true
            }
        } catch (e: Exception) {
            Log.e("RealtimeService", "Error starting foreground service or listening", e)
        }
        return START_STICKY
    }"""

content = content.replace(target, replacement)
with open(file_path, "w") as f:
    f.write(content)

