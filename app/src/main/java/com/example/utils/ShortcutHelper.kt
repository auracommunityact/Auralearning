package com.example.utils

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.widget.Toast
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder

object ShortcutHelper {

    private fun toSlug(str: String): String {
        return str.lowercase()
            .replace(Regex("[^a-z0-9\\s-]"), "")
            .replace(Regex("\\s+"), "-")
            .replace(Regex("-+"), "-")
            .trim { it == '-' }
    }

    /**
     * Helper to check if pinned shortcuts are supported.
     */
    fun isPinShortcutSupported(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
            shortcutManager?.isRequestPinShortcutSupported == true
        } else {
            false
        }
    }

    /**
     * Checks if a specific shortcut ID is currently pinned.
     */
    fun isShortcutPinned(context: Context, id: String): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return false
            return shortcutManager.pinnedShortcuts.any { it.id == id }
        }
        return false
    }

    /**
     * Create a fallback bitmap with the first letter of the item name and a colorful modern gradient.
     */
    private fun createFallbackBitmap(title: String): Bitmap {
        val size = 192
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            isAntiAlias = true
        }

        // Generate color based on title to keep it consistent
        val colors = getGradientColors(title)
        val gradient = LinearGradient(0f, 0f, size.toFloat(), size.toFloat(), colors[0], colors[1], Shader.TileMode.CLAMP)
        paint.shader = gradient
        canvas.drawRoundRect(0f, 0f, size.toFloat(), size.toFloat(), 32f, 32f, paint)

        // Draw first character
        paint.shader = null
        paint.color = Color.WHITE
        paint.textSize = size / 2f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        paint.textAlign = Paint.Align.CENTER

        val text = if (title.isNotEmpty()) title.substring(0, 1).uppercase() else "A"
        val x = size / 2f
        val y = size / 2f - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(text, x, y, paint)

        return bitmap
    }

    private fun getGradientColors(title: String): IntArray {
        val hash = title.hashCode()
        return when (Math.abs(hash) % 5) {
            0 -> intArrayOf(0xFF6366F1.toInt(), 0xFF4F46E5.toInt()) // Indigo
            1 -> intArrayOf(0xFFEC4899.toInt(), 0xFFDB2777.toInt()) // Pink
            2 -> intArrayOf(0xFF10B981.toInt(), 0xFF059669.toInt()) // Emerald
            3 -> intArrayOf(0xFFF59E0B.toInt(), 0xFFD97706.toInt()) // Amber
            else -> intArrayOf(0xFF3B82F6.toInt(), 0xFF2563EB.toInt()) // Blue
        }
    }

    /**
     * Pins a shortcut to the Home screen.
     */
    suspend fun pinShortcut(
        context: Context,
        id: String,
        title: String,
        imageUrl: String?,
        type: String, // "book", "video", "questionpaper", "tool", "course"
        internalRoute: String
    ) {
        if (!isPinShortcutSupported(context)) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "Pinned shortcuts are not supported on this launcher.", Toast.LENGTH_SHORT).show()
            }
            return
        }

        withContext(Dispatchers.IO) {
            // Generate standard deep link URL for web fallback redirect
            val slug = toSlug(title)
            val webUrl = "https://aura.auralearning.workers.dev/$type/$slug"

            // Build intent to open the content in the app
            val intent = Intent(context, com.example.MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = Uri.parse(webUrl)
                putExtra("deep_link", internalRoute)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }

            // Load icon
            val bitmap = if (!imageUrl.isNullOrBlank()) {
                try {
                    val loader = ImageLoader(context)
                    val request = ImageRequest.Builder(context)
                        .data(imageUrl)
                        .allowHardware(false)
                        .build()
                    val result = loader.execute(request)
                    val drawable = result.drawable
                    if (drawable is BitmapDrawable) {
                        drawable.bitmap
                    } else if (drawable != null) {
                        // Convert drawable to bitmap
                        val bmp = Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(128), drawable.intrinsicHeight.coerceAtLeast(128), Bitmap.Config.ARGB_8888)
                        val cvs = Canvas(bmp)
                        drawable.setBounds(0, 0, cvs.width, cvs.height)
                        drawable.draw(cvs)
                        bmp
                    } else {
                        createFallbackBitmap(title)
                    }
                } catch (e: Exception) {
                    createFallbackBitmap(title)
                }
            } else {
                createFallbackBitmap(title)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return@withContext
                
                val shortcutInfo = ShortcutInfo.Builder(context, id)
                    .setShortLabel(title)
                    .setLongLabel(title)
                    .setIcon(Icon.createWithBitmap(bitmap))
                    .setIntent(intent)
                    .build()

                val successIntent = shortcutManager.createShortcutResultIntent(shortcutInfo)
                val successCallback = PendingIntent.getBroadcast(
                    context,
                    id.hashCode(),
                    successIntent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )

                try {
                    val pinned = shortcutManager.requestPinShortcut(shortcutInfo, successCallback.intentSender)
                    withContext(Dispatchers.Main) {
                        if (pinned) {
                            Toast.makeText(context, "Adding shortcut for '$title' to Home screen...", Toast.LENGTH_LONG).show()
                        } else {
                            Toast.makeText(context, "Failed to initiate shortcut placement.", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    /**
     * Disable/remove shortcut programmatically from the app side.
     */
    fun removeShortcut(context: Context, id: String, title: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
            try {
                // We cannot delete pinned shortcuts, but we can disable them.
                shortcutManager.disableShortcuts(listOf(id), "This content shortcut has been disabled or removed.")
                Toast.makeText(context, "Shortcut for '$title' disabled. You can drag it to the trash on your home screen.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Could not disable shortcut: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Update existing shortcut if the content title or thumbnail changes.
     */
    suspend fun updateShortcut(
        context: Context,
        id: String,
        title: String,
        imageUrl: String?,
        type: String,
        internalRoute: String
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java) ?: return
            if (shortcutManager.pinnedShortcuts.any { it.id == id }) {
                withContext(Dispatchers.IO) {
                    val slug = toSlug(title)
                    val webUrl = "https://aura.auralearning.workers.dev/$type/$slug"

                    val intent = Intent(context, com.example.MainActivity::class.java).apply {
                        action = Intent.ACTION_VIEW
                        data = Uri.parse(webUrl)
                        putExtra("deep_link", internalRoute)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    }

                    val bitmap = if (!imageUrl.isNullOrBlank()) {
                        try {
                            val loader = ImageLoader(context)
                            val request = ImageRequest.Builder(context)
                                .data(imageUrl)
                                .allowHardware(false)
                                .build()
                            val result = loader.execute(request)
                            val drawable = result.drawable
                            if (drawable is BitmapDrawable) {
                                drawable.bitmap
                            } else if (drawable != null) {
                                val bmp = Bitmap.createBitmap(drawable.intrinsicWidth.coerceAtLeast(128), drawable.intrinsicHeight.coerceAtLeast(128), Bitmap.Config.ARGB_8888)
                                val cvs = Canvas(bmp)
                                drawable.setBounds(0, 0, cvs.width, cvs.height)
                                drawable.draw(cvs)
                                bmp
                            } else {
                                createFallbackBitmap(title)
                            }
                        } catch (e: Exception) {
                            createFallbackBitmap(title)
                        }
                    } else {
                        createFallbackBitmap(title)
                    }

                    val shortcutInfo = ShortcutInfo.Builder(context, id)
                        .setShortLabel(title)
                        .setLongLabel(title)
                        .setIcon(Icon.createWithBitmap(bitmap))
                        .setIntent(intent)
                        .build()

                    shortcutManager.updateShortcuts(listOf(shortcutInfo))
                }
            }
        }
    }
}
