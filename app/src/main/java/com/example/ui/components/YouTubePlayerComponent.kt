package com.example.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

private const val TAG = "YouTubePlayerComponent"

/**
 * Robust YouTube video ID extractor.
 * Handles full URLs (https://www.youtube.com/watch?v=...), short share links (https://youtu.be/...),
 * embed links, shorts, mobile links, parameters (si, feature, t), and raw 11-char IDs.
 */
fun extractYouTubeVideoId(rawVideoId: String?, rawVideoUrl: String?): String? {
    val candidates = listOfNotNull(rawVideoId, rawVideoUrl).filter { it.isNotBlank() }

    for (candidate in candidates) {
        val trimmed = candidate.trim()

        // 1. Direct 11-char ID check
        if (trimmed.length == 11 && trimmed.matches("^[a-zA-Z0-9_-]{11}$".toRegex())) {
            Log.d(TAG, "Extracted direct 11-char ID: $trimmed from candidate: '$candidate'")
            return trimmed
        }

        // 2. Parse using android.net.Uri if it's a URL
        try {
            val uri = Uri.parse(if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) "https://$trimmed" else trimmed)
            val host = uri.host?.lowercase() ?: ""

            if (host.contains("youtu.be")) {
                // Short share link e.g. https://youtu.be/dQw4w9WgXcQ?si=abcdef
                val path = uri.path?.trimStart('/') ?: ""
                val idCandidate = path.split('/').firstOrNull()?.split('?')?.firstOrNull() ?: ""
                if (idCandidate.length == 11 && idCandidate.matches("^[a-zA-Z0-9_-]{11}$".toRegex())) {
                    Log.d(TAG, "Extracted ID '$idCandidate' from youtu.be URL: '$candidate'")
                    return idCandidate
                }
            } else if (host.contains("youtube.com") || host.contains("youtube-nocookie.com")) {
                // Check query param 'v'
                val queryV = uri.getQueryParameter("v")
                if (!queryV.isNullOrBlank() && queryV.length == 11 && queryV.matches("^[a-zA-Z0-9_-]{11}$".toRegex())) {
                    Log.d(TAG, "Extracted ID '$queryV' from 'v' parameter in URL: '$candidate'")
                    return queryV
                }

                // Check paths: /embed/ID, /shorts/ID, /v/ID, /live/ID
                val segments = uri.pathSegments
                if (segments != null) {
                    for (i in 0 until segments.size) {
                        val seg = segments[i]
                        if ((seg == "embed" || seg == "shorts" || seg == "v" || seg == "live") && i + 1 < segments.size) {
                            val idCandidate = segments[i + 1]
                            if (idCandidate.length == 11 && idCandidate.matches("^[a-zA-Z0-9_-]{11}$".toRegex())) {
                                Log.d(TAG, "Extracted ID '$idCandidate' from path segment '$seg' in URL: '$candidate'")
                                return idCandidate
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Uri parsing exception for candidate: '$candidate'", e)
        }

        // 3. Fallback regex patterns for edge case links
        val patterns = listOf(
            "(?:v=|v%3D|/v/|/embed/|/shorts/|/live/|youtu\\.be/|/e/|/watch\\?v=)([a-zA-Z0-9_-]{11})".toRegex(RegexOption.IGNORE_CASE),
            "([a-zA-Z0-9_-]{11})".toRegex()
        )

        for (pattern in patterns) {
            val match = pattern.find(trimmed)
            if (match != null && match.groupValues.size > 1) {
                val extracted = match.groupValues[1]
                if (extracted.length == 11 && extracted.matches("^[a-zA-Z0-9_-]{11}$".toRegex())) {
                    Log.d(TAG, "Extracted ID '$extracted' via regex from candidate: '$candidate'")
                    return extracted
                }
            }
        }
    }

    Log.w(TAG, "Failed to extract valid YouTube video ID from rawVideoId='$rawVideoId', rawVideoUrl='$rawVideoUrl'")
    return null
}

@Composable
fun YouTubePlayerComponent(
    videoId: String?,
    videoUrl: String?,
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    onPlayerError: ((String) -> Unit)? = null
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val cleanVideoId = remember(videoId, videoUrl) {
        extractYouTubeVideoId(videoId, videoUrl)
    }

    var isPlayerLoading by remember(cleanVideoId) { mutableStateOf(true) }
    var hasError by remember(cleanVideoId) { mutableStateOf(false) }
    var errorMessage by remember(cleanVideoId) { mutableStateOf<String?>(null) }
    var useWebViewFallback by remember(cleanVideoId) { mutableStateOf(false) }
    var retryCount by remember(cleanVideoId) { mutableIntStateOf(0) }

    LaunchedEffect(videoId, videoUrl, cleanVideoId) {
        Log.d(TAG, "YouTubePlayerComponent Initialized | Original URL: '$videoUrl' | Raw Video ID: '$videoId' | Extracted Video ID: '$cleanVideoId'")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        if (cleanVideoId.isNullOrBlank()) {
            // Invalid Video ID state
            Log.e(TAG, "Invalid YouTube video link | Original URL: '$videoUrl' | Raw ID: '$videoId'")
            YouTubePlayerErrorView(
                title = "Invalid YouTube video link",
                message = "The provided link or video ID could not be recognized as a valid YouTube video.",
                onOpenExternal = {
                    val urlToOpen = videoUrl?.ifBlank { "https://www.youtube.com" } ?: "https://www.youtube.com"
                    openInYouTubeAppOrBrowser(context, urlToOpen, cleanVideoId)
                }
            )
        } else if (hasError) {
            // Error state
            Log.e(TAG, "Playback Status: ERROR | Extracted ID: '$cleanVideoId' | Message: '$errorMessage'")
            YouTubePlayerErrorView(
                title = "Playback Failed",
                message = errorMessage ?: "Unable to play video inside the app.",
                onOpenExternal = {
                    openInYouTubeAppOrBrowser(context, "https://www.youtube.com/watch?v=$cleanVideoId", cleanVideoId)
                },
                onRetry = {
                    hasError = false
                    isPlayerLoading = true
                    useWebViewFallback = false
                    retryCount++
                }
            )
        } else if (useWebViewFallback) {
            // WebView Fallback
            Log.d(TAG, "Playback Status: USING_WEBVIEW_FALLBACK | Extracted ID: '$cleanVideoId'")
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.userAgentString = "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isPlayerLoading = false
                                Log.d(TAG, "Playback Status: WEBVIEW_LOADED | Extracted ID: '$cleanVideoId'")
                            }

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                error: WebResourceError?
                            ) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    Log.e(TAG, "Playback Status: WEBVIEW_ERROR | Message: ${error?.description}")
                                    hasError = true
                                    errorMessage = "Error loading video in WebView: ${error?.description}"
                                }
                            }
                        }
                    }
                },
                update = { webView ->
                    val embedUrl = "https://www.youtube.com/embed/$cleanVideoId?autoplay=1&playsinline=1&rel=0&modestbranding=1&enablejsapi=1"
                    val html = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no"/>
                            <style>
                                * { margin: 0; padding: 0; box-sizing: border-box; }
                                body, html { width: 100%; height: 100%; background-color: #000; overflow: hidden; }
                                .container { position: relative; width: 100vw; height: 100vh; }
                                iframe { position: absolute; top: 0; left: 0; width: 100%; height: 100%; border: none; }
                            </style>
                        </head>
                        <body>
                            <div class="container">
                                <iframe src="$embedUrl" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" allowfullscreen></iframe>
                            </div>
                        </body>
                        </html>
                    """.trimIndent()
                    webView.loadDataWithBaseURL("https://www.youtube.com", html, "text/html", "UTF-8", null)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Native PierfrancescoSoffritti Android YouTube Player
            key(cleanVideoId, retryCount) {
                AndroidView(
                    factory = { ctx ->
                        YouTubePlayerView(ctx).apply {
                            enableAutomaticInitialization = false

                            val observer = object : LifecycleEventObserver {
                                override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: Lifecycle.Event) {
                                    when (event) {
                                        Lifecycle.Event.ON_RESUME -> Log.d(TAG, "Lifecycle ON_RESUME | Video ID: '$cleanVideoId'")
                                        Lifecycle.Event.ON_PAUSE -> Log.d(TAG, "Lifecycle ON_PAUSE | Video ID: '$cleanVideoId'")
                                        Lifecycle.Event.ON_DESTROY -> {
                                            Log.d(TAG, "Lifecycle ON_DESTROY | Releasing player for Video ID: '$cleanVideoId'")
                                            release()
                                        }
                                        else -> {}
                                    }
                                }
                            }
                            lifecycleOwner.lifecycle.addObserver(observer)

                            val iFramePlayerOptions = IFramePlayerOptions.Builder()
                                .controls(1)
                                .autoplay(if (autoPlay) 1 else 0)
                                .rel(0)
                                .fullscreen(0)
                                .build()

                            initialize(
                                object : AbstractYouTubePlayerListener() {
                                    override fun onReady(youTubePlayer: YouTubePlayer) {
                                        Log.d(TAG, "Playback Status: READY | Extracted Video ID: '$cleanVideoId'")
                                        isPlayerLoading = false
                                        if (autoPlay) {
                                            Log.d(TAG, "Starting playback (loadVideo) for Video ID: '$cleanVideoId'")
                                            youTubePlayer.loadVideo(cleanVideoId, 0f)
                                        } else {
                                            Log.d(TAG, "Cueing video (cueVideo) for Video ID: '$cleanVideoId'")
                                            youTubePlayer.cueVideo(cleanVideoId, 0f)
                                        }
                                    }

                                    override fun onStateChange(youTubePlayer: YouTubePlayer, state: PlayerConstants.PlayerState) {
                                        Log.d(TAG, "Playback Status: $state | Video ID: '$cleanVideoId'")
                                        when (state) {
                                            PlayerConstants.PlayerState.PLAYING,
                                            PlayerConstants.PlayerState.VIDEO_CUED,
                                            PlayerConstants.PlayerState.PAUSED -> {
                                                isPlayerLoading = false
                                            }
                                            PlayerConstants.PlayerState.BUFFERING -> {
                                                Log.d(TAG, "Playback Status: BUFFERING | Video ID: '$cleanVideoId'")
                                            }
                                            PlayerConstants.PlayerState.ENDED -> {
                                                Log.d(TAG, "Playback Status: ENDED | Video ID: '$cleanVideoId'")
                                            }
                                            else -> {}
                                        }
                                    }

                                    override fun onError(youTubePlayer: YouTubePlayer, error: PlayerConstants.PlayerError) {
                                        Log.e(TAG, "Playback Status: ERROR ($error) | Video ID: '$cleanVideoId'")
                                        isPlayerLoading = false
                                        
                                        val msg = when (error) {
                                            PlayerConstants.PlayerError.VIDEO_NOT_PLAYABLE_IN_EMBEDDED_PLAYER ->
                                                "Embedding disabled by owner for this video."
                                            PlayerConstants.PlayerError.VIDEO_NOT_FOUND ->
                                                "Video not found or has been removed."
                                            PlayerConstants.PlayerError.INVALID_PARAMETER_IN_REQUEST ->
                                                "Invalid video request parameter."
                                            else ->
                                                "Playback error: $error"
                                        }
                                        
                                        onPlayerError?.invoke(msg)

                                        // Fallback to WebView if error is generic or invalid parameter
                                        if (error == PlayerConstants.PlayerError.UNKNOWN || error == PlayerConstants.PlayerError.INVALID_PARAMETER_IN_REQUEST) {
                                            Log.w(TAG, "Attempting WebView fallback for error $error on Video ID: '$cleanVideoId'")
                                            useWebViewFallback = true
                                        } else {
                                            hasError = true
                                            errorMessage = msg
                                        }
                                    }
                                },
                                true,
                                iFramePlayerOptions
                            )
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Loading overlay
        if (isPlayerLoading && !hasError && !cleanVideoId.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Loading YouTube Player...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun YouTubePlayerErrorView(
    title: String,
    message: String,
    onOpenExternal: () -> Unit,
    onRetry: (() -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Block,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(40.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (onRetry != null) {
                OutlinedButton(onClick = onRetry) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Retry")
                }
            }
            Button(
                onClick = onOpenExternal,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF0000))
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Open YouTube")
            }
        }
    }
}

fun openInYouTubeAppOrBrowser(context: Context, fullUrl: String, videoId: String?) {
    val cleanId = videoId ?: extractYouTubeVideoId(null, fullUrl)
    val appIntent = if (!cleanId.isNullOrBlank()) {
        Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$cleanId"))
    } else null

    val browserIntent = Intent(
        Intent.ACTION_VIEW,
        Uri.parse(if (!cleanId.isNullOrBlank()) "https://www.youtube.com/watch?v=$cleanId" else fullUrl)
    )

    try {
        if (appIntent != null) {
            context.startActivity(appIntent)
        } else {
            context.startActivity(browserIntent)
        }
    } catch (ex: ActivityNotFoundException) {
        try {
            context.startActivity(browserIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open YouTube in browser: ${e.message}")
        }
    }
}
