import re

file_path = "app/src/main/java/com/example/MainActivity.kt"
with open(file_path, "r") as f:
    content = f.read()

pattern = r"// Initialize AdMob Mobile Ads SDK optimally on startup.*?// Start Realtime Notification Service\s*try \{\s*com\.example\.notifications\.RealtimeNotificationService\.start\(this\)\s*\} catch \(e: Exception\) \{\s*android\.util\.Log\.e\(\"MainActivity\", \"RealtimeNotificationService start error\", e\)\s*\}"

replacement = """// Register all notification categories/channels with the Android system
        try {
            com.example.utils.NotificationHelper.registerNotificationChannels(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "NotificationHelper error", e)
        }
        
        // Asynchronously initialize non-critical components after UI becomes active
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

if re.search(pattern, content, flags=re.DOTALL):
    content = re.sub(pattern, replacement, content, flags=re.DOTALL)
    if "import androidx.lifecycle.lifecycleScope" not in content:
        content = content.replace("import androidx.compose.ui.Modifier", "import androidx.compose.ui.Modifier\nimport androidx.lifecycle.lifecycleScope")
    with open(file_path, "w") as f:
        f.write(content)
    print("MainActivity patched successfully.")
else:
    print("Pattern not found in MainActivity.")
