import re

file_path = "app/src/main/java/com/example/AuraApplication.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """    override fun onCreate() {
        super.onCreate()
        instance = this
        AppContext.init(this)

        try {
            FirebaseApp.initializeApp(this)
        } catch (e: Exception) {
            Log.e("AuraApp", "Firebase init error", e)
        }

        try {
            com.example.utils.NotificationHelper.registerNotificationChannels(this)
        } catch (e: Exception) {
            Log.e("AuraApp", "NotificationHelper error", e)
        }

        try {
            val codeCacheDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache")
            if (codeCacheDir.exists()) {
                codeCacheDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.e("AuraApp", "WebView cache cleanup error", e)
        }
    }"""

replacement = """    val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this
        AppContext.init(this)

        applicationScope.launch {
            try {
                FirebaseApp.initializeApp(this@AuraApplication)
            } catch (e: Exception) {
                Log.e("AuraApp", "Firebase init error", e)
            }

            try {
                com.example.utils.NotificationHelper.registerNotificationChannels(this@AuraApplication)
            } catch (e: Exception) {
                Log.e("AuraApp", "NotificationHelper error", e)
            }

            try {
                val codeCacheDir = java.io.File(cacheDir, "WebView/Default/HTTP Cache/Code Cache")
                if (codeCacheDir.exists()) {
                    codeCacheDir.deleteRecursively()
                }
            } catch (e: Exception) {
                Log.e("AuraApp", "WebView cache cleanup error", e)
            }
            
            // Pre-warm Supabase initialization in background to prevent first-access blocking
            try {
                com.example.data.supabase.SupabaseService.client
            } catch (e: Exception) {
                Log.e("AuraApp", "Supabase init error", e)
            }
        }
    }"""

if target in content:
    content = content.replace(target, replacement)
    if "import kotlinx.coroutines.launch" not in content:
        content = content.replace("import com.google.firebase.FirebaseApp", "import com.google.firebase.FirebaseApp\nimport kotlinx.coroutines.launch")
    with open(file_path, "w") as f:
        f.write(content)
    print("AuraApplication patched successfully.")
else:
    print("Target not found.")

