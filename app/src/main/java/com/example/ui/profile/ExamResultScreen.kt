package com.example.ui.profile

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

import com.example.data.models.TimestampSerializer
import kotlinx.serialization.Serializable

@Serializable
data class BoardResult(
    val id: String = "",
    val board: String = "",
    val website: String = "",
    @Serializable(with = TimestampSerializer::class)
    val createdAt: Long = 0L
)

val boardsJson = """
[
  { "board": "CBSE", "website": "https://results.cbse.nic.in" },
  { "board": "ICSE", "website": "https://cisce.org" },
  { "board": "Haryana Board (HBSE)", "website": "https://bseh.org.in" },
  { "board": "Rajasthan Board (RBSE)", "website": "https://rajeduboard.rajasthan.gov.in" },
  { "board": "Uttar Pradesh Board (UPMSP)", "website": "https://upmsp.edu.in" },
  { "board": "Bihar Board (BSEB)", "website": "https://biharboardonline.bihar.gov.in" },
  { "board": "Madhya Pradesh Board (MPBSE)", "website": "https://mpbse.nic.in" },
  { "board": "Maharashtra Board (MSBSHSE)", "website": "https://mahahsscboard.in" },
  { "board": "Punjab Board (PSEB)", "website": "https://pseb.ac.in" },
  { "board": "Gujarat Board (GSEB)", "website": "https://gseb.org" },
  { "board": "Odisha Board", "website": "https://bseodisha.ac.in" },
  { "board": "Jharkhand Board (JAC)", "website": "https://jac.jharkhand.gov.in" },
  { "board": "Chhattisgarh Board (CGBSE)", "website": "https://cgbse.nic.in" },
  { "board": "Uttarakhand Board (UBSE)", "website": "https://ubse.uk.gov.in" },
  { "board": "Himachal Pradesh Board (HPBOSE)", "website": "https://hpbose.org" },
  { "board": "Jammu & Kashmir Board (JKBOSE)", "website": "https://jkbose.nic.in" },
  { "board": "Karnataka Board", "website": "https://kseab.karnataka.gov.in" },
  { "board": "Kerala Board", "website": "https://keralaresults.nic.in" },
  { "board": "Tamil Nadu Board", "website": "https://tnresults.nic.in" },
  { "board": "Telangana Board", "website": "https://bse.telangana.gov.in" },
  { "board": "Andhra Pradesh Board", "website": "https://bse.ap.gov.in" },
  { "board": "Assam Board (ASSEB)", "website": "https://site.sebaonline.org" },
  { "board": "West Bengal Board (WBBSE)", "website": "https://wbbse.wb.gov.in" },
  { "board": "Goa Board (GBSHSE)", "website": "https://gbshse.in" },
  { "board": "Tripura Board (TBSE)", "website": "https://tbse.tripura.gov.in" },
  { "board": "Meghalaya Board (MBOSE)", "website": "https://mbose.in" },
  { "board": "Mizoram Board (MBSE)", "website": "https://mbse.edu.in" },
  { "board": "Nagaland Board (NBSE)", "website": "https://nbsenl.edu.in" },
  { "board": "Manipur Board (BOSEM)", "website": "https://manresults.nic.in" },
  { "board": "Sikkim Board", "website": "https://sikkimhrdd.org" },
  { "board": "Arunachal Pradesh Board", "website": "https://apdhte.nic.in" }
]
"""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExamResultScreen(navController: NavController, rootNavController: NavController) {
    val repository = remember { com.example.data.repository.AuraRepository() }
    var dynamicBoards by remember { mutableStateOf<List<BoardResult>>(emptyList()) }

    LaunchedEffect(Unit) {
        com.example.data.repository.AuraRepository.boardsUpdateTrigger.collect {
            dynamicBoards = repository.getExamBoards()
        }
    }

    val staticBoards: List<BoardResult> = remember {
        val type = object : TypeToken<List<BoardResult>>() {}.type
        Gson().fromJson<List<BoardResult>>(boardsJson, type) ?: emptyList()
    }

    val allCombinedBoards: List<BoardResult> = remember(staticBoards, dynamicBoards) {
        dynamicBoards + staticBoards
    }

    var searchQuery by remember { mutableStateOf("") }

    val filteredBoards: List<BoardResult> = remember(searchQuery, allCombinedBoards) {
        allCombinedBoards.filter { it.board.contains(searchQuery, ignoreCase = true) }
    }

    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("ExamResultsPrefs", Context.MODE_PRIVATE) }
    var recentBoards by remember {
        mutableStateOf(prefs.getStringSet("recentBoards", emptySet())?.toList() ?: emptyList())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("🎓 Check Exam Result") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("Search boards...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
                shape = RoundedCornerShape(12.dp),
                singleLine = true
            )

            Text(
                "Select your board to open the official result website.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (searchQuery.isEmpty() && recentBoards.isNotEmpty()) {
                    item {
                        Text(
                            "Recently Opened",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    val recents = allCombinedBoards.filter { recentBoards.contains(it.board) }.sortedBy { recentBoards.indexOf(it.board) }
                    items(recents) { board ->
                        BoardItemCard(board = board, onBoardClick = {
                            val currentRecent = recentBoards.toMutableList()
                            currentRecent.remove(board.board)
                            currentRecent.add(0, board.board)
                            val newRecent = currentRecent.take(5)
                            recentBoards = newRecent
                            prefs.edit().putStringSet("recentBoards", newRecent.toSet()).apply()

                            val encodedUrl = java.net.URLEncoder.encode(board.website, "UTF-8")
                            val encodedTitle = java.net.URLEncoder.encode(board.board, "UTF-8")
                            rootNavController.navigate("exam_webview?url=${encodedUrl}&title=${encodedTitle}")
                        })
                    }
                    item {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "All Boards",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                }

                items(filteredBoards) { board ->
                    BoardItemCard(board = board, onBoardClick = {
                        val currentRecent = recentBoards.toMutableList()
                        currentRecent.remove(board.board)
                        currentRecent.add(0, board.board)
                        val newRecent = currentRecent.take(5)
                        recentBoards = newRecent
                        prefs.edit().putStringSet("recentBoards", newRecent.toSet()).apply()

                        val encodedUrl = java.net.URLEncoder.encode(board.website, "UTF-8")
                        val encodedTitle = java.net.URLEncoder.encode(board.board, "UTF-8")
                        rootNavController.navigate("exam_webview?url=${encodedUrl}&title=${encodedTitle}")
                    })
                }
            }
        }
    }
}

@Composable
fun BoardItemCard(board: BoardResult, onBoardClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onBoardClick() },
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = board.board.take(1),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = board.board,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { onBoardClick() }) {
                Text("Open Result Portal")
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultWebViewScreen(navController: NavController, url: String, title: String) {
    var isLoading by remember { mutableStateOf(true) }
    var isError by remember { mutableStateOf(false) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    val context = LocalContext.current

    androidx.activity.compose.BackHandler(enabled = canGoBack) {
        webViewInstance?.goBack()
    }

    Scaffold(
        topBar = {
            if (title != "Search" && title != "AI Mode") {
                TopAppBar(
                    title = { Text(title, maxLines = 1) },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (canGoBack) {
                                webViewInstance?.goBack()
                            } else {
                                navController.popBackStack()
                            }
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, webViewInstance?.url ?: url)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share URL"))
                        }) {
                            Icon(Icons.Filled.Share, contentDescription = "Share Page")
                        }
                        IconButton(onClick = { webViewInstance?.reload() }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                        }
                    }
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (isError) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Failed to load website", color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        isError = false
                        isLoading = true
                        webViewInstance?.reload()
                    }) {
                        Text("Retry")
                    }
                }
            } else {
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.domStorageEnabled = true
                            
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                    super.onPageStarted(view, url, favicon)
                                    isLoading = true
                                    canGoBack = view?.canGoBack() == true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    super.onPageFinished(view, url)
                                    isLoading = false
                                    canGoBack = view?.canGoBack() == true
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    request: WebResourceRequest?,
                                    error: WebResourceError?
                                ) {
                                    super.onReceivedError(view, request, error)
                                    isLoading = false
                                    isError = true
                                }
                            }
                            loadUrl(url)
                            webViewInstance = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }
}
