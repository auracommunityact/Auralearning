package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.ui.theme.AuraTheme
import com.example.ui.theme.ThemeViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Register all notification categories/channels with the Android system
        try {
            com.example.utils.NotificationHelper.registerNotificationChannels(this)
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "NotificationHelper error", e)
        }
        
        // Asynchronously initialize non-critical components using lifecycleScope with Dispatchers.IO
        lifecycleScope.launch(Dispatchers.IO) {
            delay(1500) // Delay to ensure splash/UI is fully drawn
            
            try {
                com.example.notifications.RealtimeNotificationService.start(this@MainActivity)
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "RealtimeNotificationService start error", e)
            }
            
            // AdsManager initialization requires Main thread for ProcessLifecycleOwner & UI operations
            withContext(Dispatchers.Main) {
                try {
                    com.example.utils.AdsManager.initialize(application, this@MainActivity)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "AdsManager init error", e)
                }
            }
        }
        
        val themeViewModel = ThemeViewModel(this)
        
        val intentData = intent.data
        var initialDeepLink = intent.getStringExtra("deep_link")
        if (initialDeepLink.isNullOrBlank() && intent.action == "com.example.ACTION_GLOBAL_SEARCH") {
            val query = intent.getStringExtra("query") ?: ""
            initialDeepLink = if (query.isNotBlank()) "global_search?query=${android.net.Uri.encode(query)}" else "global_search"
        }
        if (initialDeepLink.isNullOrBlank() && intentData != null && (intentData.host == "auralearningwebsite.netlify.app" || intentData.host == "aura.auralearning.workers.dev")) {
            val path = intentData.path
            val bookParam = intentData.getQueryParameter("book")
            initialDeepLink = when {
                bookParam != null -> "deeplink_loader?type=book&slug=${android.net.Uri.encode(bookParam)}"
                path?.startsWith("/course/") == true -> {
                    val slug = path.substringAfter("/course/")
                    "deeplink_loader?type=course&slug=${android.net.Uri.encode(slug)}"
                }
                path?.startsWith("/video/") == true -> {
                    val slug = path.substringAfter("/video/")
                    "deeplink_loader?type=video&slug=${android.net.Uri.encode(slug)}"
                }
                path?.startsWith("/questionpaper/") == true -> {
                    val slug = path.substringAfter("/questionpaper/")
                    "deeplink_loader?type=questionPaper&slug=${android.net.Uri.encode(slug)}"
                }
                path?.startsWith("/tool/") == true -> {
                    val slug = path.substringAfter("/tool/")
                    "deeplink_loader?type=tool&slug=${android.net.Uri.encode(slug)}"
                }
                path?.startsWith("/u/") == true -> {
                    val userId = path.substringAfter("/u/")
                    "profile_details/$userId"
                }
                path?.startsWith("/book/") == true -> {
                    val slug = path.substringAfter("/book/")
                    "deeplink_loader?type=book&slug=${android.net.Uri.encode(slug)}"
                }
                path?.startsWith("/page/") == true -> {
                    val slug = path.substringAfter("/page/")
                    "deeplink_loader?type=page&slug=${android.net.Uri.encode(slug)}"
                }
                path == "/ai_chat" || path?.startsWith("/ai_chat") == true -> {
                    val promptParam = intentData.getQueryParameter("prompt")
                    if (promptParam != null) "ai_chat?prompt=${android.net.Uri.encode(promptParam)}" else "ai_chat"
                }
                path == "/courses" || path?.startsWith("/courses") == true -> "courses"
                path == "/pdf_tool" || path?.startsWith("/pdf_tool") == true -> "pdf_tool"
                path?.startsWith("/book_detail/") == true -> {
                    val bookId = path.substringAfter("/book_detail/")
                    "book_detail/$bookId"
                }
                path?.startsWith("/video_player/") == true -> {
                    val videoId = path.substringAfter("/video_player/")
                    "video_player/$videoId"
                }
                else -> {
                    val tabParam = intentData.getQueryParameter("tab")
                    if (tabParam != null) "main?tab=$tabParam" else "main?tab=home"
                }
            }
        }
        
        setContent {
            val themeMode by themeViewModel.themeMode.collectAsState()
            val useDarkTheme = when (themeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }
            
            AuraTheme(useDarkTheme = useDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    com.example.ui.components.ErrorBoundary {
                        AuraLearningApp(themeViewModel = themeViewModel, initialDeepLink = initialDeepLink)
                    }
                }
            }
        }
    }

    val isInPipMode = kotlinx.coroutines.flow.MutableStateFlow(false)

    @android.annotation.SuppressLint("NewApi")
    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipMode.value = isInPictureInPictureMode
    }

    override fun onResume() {
        super.onResume()
        com.example.ui.chat.UserPresenceManager.setOnline()
    }
    
    override fun onPause() {
        super.onPause()
        com.example.ui.chat.UserPresenceManager.setOffline()
    }
    
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}
