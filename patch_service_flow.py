import re

file_path = "app/src/main/java/com/example/notifications/RealtimeNotificationService.kt"
with open(file_path, "r") as f:
    content = f.read()

target = """.onEach { action ->
            val data = action.record"""

replacement = """.onEach { action ->
            val data = action.record"""

if target in content:
    # Just replace the catch block for the flow
    content = content.replace("}.launchIn(serviceScope)", """}.catch { e ->
            Log.e("RealtimeService", "Flow error", e)
        }.launchIn(serviceScope)""")
    if "import kotlinx.coroutines.flow.catch" not in content:
        content = content.replace("import kotlinx.coroutines.flow.onEach", "import kotlinx.coroutines.flow.onEach\nimport kotlinx.coroutines.flow.catch")
    with open(file_path, "w") as f:
        f.write(content)
    print("RealtimeNotificationService patched flow successfully.")
else:
    print("Target not found.")

