import re

file_path = "app/src/main/java/com/example/MainActivity.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """        // Asynchronously initialize non-critical components after UI becomes active
        androidx.lifecycle.lifecycleScope.launchWhenResumed {
            kotlinx.coroutines.delay(1500) // Delay to ensure splash/UI is fully drawn
            try {
                com.example.utils.AdsManager.initialize(application, this@MainActivity)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "AdsManager init error", e)
            }
            try {
                com.example.notifications.RealtimeNotificationService.start(this@MainActivity)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "RealtimeNotificationService start error", e)
            }
        }"""

replacement = """        // Asynchronously initialize non-critical components using a custom CoroutineScope with Dispatchers.IO
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO).launch {
            kotlinx.coroutines.delay(1500) // Delay to ensure splash/UI is fully drawn
            
            try {
                com.example.notifications.RealtimeNotificationService.start(this@MainActivity)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "RealtimeNotificationService start error", e)
            }
            
            // AdsManager initialization requires Main thread for ProcessLifecycleOwner & UI operations
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                try {
                    com.example.utils.AdsManager.initialize(application, this@MainActivity)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "AdsManager init error", e)
                }
            }
        }"""

if target in content:
    content = content.replace(target, replacement)
    with open(file_path, "w") as f:
        f.write(content)
    print("MainActivity patched successfully.")
else:
    print("Target not found.")

