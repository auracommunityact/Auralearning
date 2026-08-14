package com.example

import android.app.Application
import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import kotlinx.coroutines.launch

object AppContext {
    private var _context: Context? = null

    var context: Context
        get() = _context ?: AuraApplication.instance
        set(value) {
            _context = value.applicationContext
        }

    fun init(context: Context) {
        _context = context.applicationContext
    }

    val safeContext: Context?
        get() = _context ?: try { AuraApplication.instance } catch (e: Exception) { null }
}

class AuraApplication : Application() {
    companion object {
        lateinit var instance: AuraApplication
            private set
    }

    val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO)

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
    }
}

