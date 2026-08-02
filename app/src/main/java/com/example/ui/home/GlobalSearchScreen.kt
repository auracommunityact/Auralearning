package com.example.ui.home

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import coil.compose.AsyncImage
import com.example.data.models.User
import com.example.data.models.Book
import com.example.data.models.Video
import com.example.data.models.QuestionPaper
import com.example.data.models.Website
import com.example.ui.ViewModelFactory
import com.example.ui.profile.BoardResult
import com.example.ui.profile.boardsJson
import com.example.ui.study.StudyTool
import com.example.ui.study.allStudyTools
import com.example.utils.VoiceSearchHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.net.URLEncoder
import kotlinx.coroutines.delay

data class HistoryItem(
    val query: String,
    val isPinned: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

data class LocalAiAnswer(
    val content: String,
    val relatedBooks: List<Book> = emptyList(),
    val relatedVideos: List<Video> = emptyList(),
)

fun scoreBook(book: Book, query: String): Int {
    val q = query.lowercase().trim()
    if (q.isEmpty()) return 0
    val title = book.bookName.lowercase()
    val subject = book.subject.lowercase()
    var score = 0
    if (title == q) score += 500
    else if (title.startsWith(q)) score += 300
    else if (title.contains(q)) score += 150
    if (subject.contains(q)) score += 100
    return score
}

fun scoreVideo(video: Video, query: String): Int {
    val q = query.lowercase().trim()
    if (q.isEmpty()) return 0
    val title = video.title.lowercase()
    val teacher = video.teacher.lowercase()
    val subject = video.subject.lowercase()
    var score = 0
    if (title == q) score += 500
    else if (title.startsWith(q)) score += 300
    else if (title.contains(q)) score += 150
    if (teacher.contains(q)) score += 100
    if (subject.contains(q)) score += 80
    return score
}

fun scoreQuestionPaper(paper: QuestionPaper, query: String): Int {
    val q = query.lowercase().trim()
    if (q.isEmpty()) return 0
    val title = paper.title.lowercase()
    val subject = paper.subject.lowercase()
    val board = paper.board.lowercase()
    var score = 0
    if (title == q) score += 500
    else if (title.startsWith(q)) score += 300
    else if (title.contains(q)) score += 150
    if (subject.contains(q)) score += 100
    if (board.contains(q)) score += 80
    return score
}

fun scoreTool(tool: StudyTool, query: String): Int {
    val q = query.lowercase().trim()
    if (q.isEmpty()) return 0
    if (tool.title.lowercase().contains(q)) return 100
    return 0
}

fun scoreWebsite(w: Website, query: String): Int {
    val q = query.lowercase().trim()
    if (q.isEmpty()) return 0
    if (w.name.lowercase().contains(q)) return 100
    return 0
}

fun scoreBoard(b: BoardResult, query: String): Int {
    val q = query.lowercase().trim()
    if (q.isEmpty()) return 0
    if (b.board.lowercase().contains(q)) return 100
    return 0
}

@Composable
fun GoogleSearchCard(
    category: String,
    categoryIcon: ImageVector,
    displayPath: String,
    title: String,
    description: String,
    thumbnail: String?,
    gradeText: String?,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1E293B)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = categoryIcon,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "$category • $displayPath",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF38BDF8),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCBD5E1),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (!thumbnail.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    AsyncImage(
                        model = thumbnail,
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF0F172A)),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            if (!gradeText.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color(0xFF334155),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    Text(
                        text = gradeText,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFFF1F5F9),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchScreen(
    navController: NavController,
    rootNavController: NavController,
    initialQuery: String = "",
    viewModel: HomeViewModel = viewModel(factory = ViewModelFactory)
) {
    val context = LocalContext.current
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current

    // Data from ViewModel
    val allBooks by viewModel.allBooks.collectAsState()
    val allVideos by viewModel.allVideos.collectAsState()
    val allQuestionPapers by viewModel.allQuestionPapers.collectAsState()
    val websites by viewModel.allWebsites.collectAsState()
    val userSearchResults by viewModel.userSearchResults.collectAsState()

    val dynamicBoards = remember { mutableStateListOf<BoardResult>() }
    val staticBoards = remember {
        try {
            val type = object : TypeToken<List<BoardResult>>() {}.type
            Gson().fromJson<List<BoardResult>>(boardsJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList<BoardResult>()
        }
    }
    val boards = remember(staticBoards, dynamicBoards) {
        dynamicBoards + staticBoards
    }

    // Search and Suggestion State
    var searchQuery by remember { mutableStateOf(initialQuery) }
    var isSearchConfirmed by remember { mutableStateOf(initialQuery.isNotBlank()) }

    // History Preference management
    val sharedPreferences = remember(context) {
        context.getSharedPreferences("aura_search_prefs", Context.MODE_PRIVATE)
    }
    var historyList by remember {
        mutableStateOf<List<HistoryItem>>(emptyList())
    }

    fun loadHistory(): List<HistoryItem> {
        val json = sharedPreferences.getString("history_list", null)
        if (json.isNullOrBlank()) {
            return listOf(
                HistoryItem("Maths", isPinned = false),
                HistoryItem("Class 10 Science", isPinned = false),
                HistoryItem("Kritika", isPinned = false),
                HistoryItem("Rajasthan Result", isPinned = false),
                HistoryItem("PDF Reader", isPinned = false)
            )
        }
        return try {
            val type = object : TypeToken<List<HistoryItem>>() {}.type
            Gson().fromJson<List<HistoryItem>>(json, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    LaunchedEffect(Unit) {
        historyList = loadHistory()
        delay(150)
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    fun saveHistory(newList: List<HistoryItem>) {
        val sortedList = newList.sortedWith(
            compareByDescending<HistoryItem> { it.isPinned }
                .thenByDescending { it.timestamp }
        )
        historyList = sortedList
        val json = Gson().toJson(sortedList)
        sharedPreferences.edit().putString("history_list", json).apply()
    }

    fun addToHistory(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val existing = historyList.find { it.query.equals(trimmed, ignoreCase = true) }
        val newList = historyList.filterNot { it.query.equals(trimmed, ignoreCase = true) }.toMutableList()
        
        val newItem = HistoryItem(
            query = existing?.query ?: trimmed,
            isPinned = existing?.isPinned ?: false,
            timestamp = System.currentTimeMillis()
        )
        newList.add(0, newItem)
        
        val cappedList = if (newList.size > 20) {
            val unpinned = newList.filter { !it.isPinned }
            if (unpinned.isNotEmpty()) {
                val oldestUnpinned = unpinned.minByOrNull { it.timestamp }
                newList.filterNot { it == oldestUnpinned }
            } else {
                newList.take(20)
            }
        } else {
            newList
        }
        saveHistory(cappedList)
    }

    fun togglePin(item: HistoryItem) {
        val newList = historyList.map {
            if (it.query == item.query) {
                it.copy(isPinned = !it.isPinned, timestamp = System.currentTimeMillis())
            } else {
                it
            }
        }
        saveHistory(newList)
    }

    fun deleteHistoryItem(item: HistoryItem) {
        val newList = historyList.filterNot { it.query == item.query }
        saveHistory(newList)
    }

    // Speech Recognition Setup
    val voiceHelper = remember { VoiceSearchHelper(context) }
    val speechResult by voiceHelper.speechResult.collectAsState()
    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            voiceHelper.startListening()
        }
    }

    LaunchedEffect(speechResult) {
        if (speechResult.isNotEmpty()) {
            if (!com.example.utils.NetworkUtils.isNetworkAvailable(context)) {
                android.widget.Toast.makeText(context, "Offline: Please check your internet connection.", android.widget.Toast.LENGTH_SHORT).show()
            } else {
                searchQuery = speechResult
                isSearchConfirmed = true
                addToHistory(speechResult)
                focusManager.clearFocus()
                voiceHelper.clearSpeechResult()
            }
        }
    }

    // Dynamic suggestions based on query
    val suggestions = remember(searchQuery, historyList) {
        if (searchQuery.isBlank()) return@remember emptyList<String>()
        val q = searchQuery.lowercase().trim()
        val result = mutableSetOf<String>()
        
        historyList.forEach { item ->
            if (item.query.lowercase().contains(q)) {
                result.add(item.query)
            }
        }
        
        val smartPredefined = listOf(
            "Maths", "Science", "Social Science", "Hindi", "English", "Sanskrit",
            "Rajasthan Result", "RBSE 10th Result", "CBSE Board Result",
            "Question Bank", "Sample Paper", "Model Paper"
        )
        smartPredefined.forEach { s ->
            if (s.lowercase().contains(q)) {
                result.add(s)
            }
        }
        
        allBooks.forEach { book ->
            if (book.bookName.lowercase().contains(q)) result.add(book.bookName)
        }
        allVideos.forEach { video ->
            if (video.title.lowercase().contains(q)) result.add(video.title)
        }
        allQuestionPapers.forEach { paper ->
            if (paper.title.lowercase().contains(q)) result.add(paper.title)
        }
        
        result.filter { it.isNotBlank() }
            .sortedWith(compareBy(
                { !it.lowercase().startsWith(q) },
                { it.length }
            ))
            .take(12)
    }

    // Dynamic Search Results scoring and ranking
    val scoredWebsites = remember(searchQuery, websites) {
        websites.map { it to scoreWebsite(it, searchQuery) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
    val scoredBoards = remember(searchQuery, boards) {
        boards.map { it to scoreBoard(it, searchQuery) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
    val scoredQuestionPapers = remember(searchQuery, allQuestionPapers) {
        allQuestionPapers.map { it to scoreQuestionPaper(it, searchQuery) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
    val scoredBooks = remember(searchQuery, allBooks) {
        allBooks.map { it to scoreBook(it, searchQuery) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
    val scoredVideos = remember(searchQuery, allVideos) {
        allVideos.map { it to scoreVideo(it, searchQuery) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }
    val scoredTools = remember(searchQuery) {
        allStudyTools.map { it to scoreTool(it, searchQuery) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .map { it.first }
    }

    val hasAnyResults = scoredQuestionPapers.isNotEmpty() || scoredBooks.isNotEmpty() || 
                       scoredVideos.isNotEmpty() || scoredTools.isNotEmpty() || 
                       userSearchResults.isNotEmpty() || scoredWebsites.isNotEmpty() ||
                       scoredBoards.isNotEmpty()

    val onBackAction: () -> Unit = {
        if (isSearchConfirmed) {
            isSearchConfirmed = false
        } else {
            val popped = navController.popBackStack()
            if (!popped) {
                rootNavController.navigate("main?tab=home") {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
        Unit
    }

    BackHandler {
        onBackAction()
    }

    var selectedTab by remember { mutableStateOf(0) }
    LaunchedEffect(searchQuery, isSearchConfirmed) {
        if (isSearchConfirmed) {
            viewModel.searchUsers(searchQuery)
        }
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .background(Color(0xFF0F172A))) {
        
        // Search Header
        Surface(
            color = Color(0xFF1E293B),
            tonalElevation = 4.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBackAction) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .background(Color(0xFF334155), RoundedCornerShape(24.dp))
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { 
                                searchQuery = it
                                isSearchConfirmed = false
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color.White),
                            cursorBrush = SolidColor(Color(0xFF38BDF8)),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {
                                if (searchQuery.isNotBlank()) {
                                    if (!com.example.utils.NetworkUtils.isNetworkAvailable(context)) {
                                        android.widget.Toast.makeText(context, "Offline: Please check your internet connection.", android.widget.Toast.LENGTH_SHORT).show()
                                    } else {
                                        isSearchConfirmed = true
                                        addToHistory(searchQuery)
                                        focusManager.clearFocus()
                                    }
                                }
                            }),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Search Aura Learning...", color = Color(0xFF94A3B8))
                                }
                                innerTextField()
                            }
                        )
                    }
                    
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = ""; isSearchConfirmed = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Color(0xFF94A3B8))
                        }
                    } else {
                        IconButton(onClick = {
                            recordAudioPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                        }) {
                            Icon(Icons.Default.Mic, contentDescription = "Voice Search", tint = Color(0xFF94A3B8))
                        }
                    }
                }
                
                if (isSearchConfirmed) {
                    ScrollableTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        contentColor = Color(0xFF38BDF8),
                        divider = {},
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = Color(0xFF38BDF8)
                            )
                        },
                        edgePadding = 16.dp
                    ) {
                        val tabs = listOf("All", "Books", "Question Papers", "Videos", "Users", "Results")
                        tabs.forEachIndexed { index, title ->
                            Tab(
                                selected = selectedTab == index,
                                onClick = { selectedTab = index },
                                text = { Text(title, style = MaterialTheme.typography.labelLarge) },
                                unselectedContentColor = Color(0xFF94A3B8)
                            )
                        }
                    }
                }
            }
        }

        // Content
        Box(modifier = Modifier.weight(1f)) {
            if (!isSearchConfirmed) {
                // Suggestions and History
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    if (searchQuery.isNotEmpty()) {
                        items(suggestions) { suggestion ->
                            SuggestionItem(suggestion) {
                                if (!com.example.utils.NetworkUtils.isNetworkAvailable(context)) {
                                    android.widget.Toast.makeText(context, "Offline: Please check your internet connection.", android.widget.Toast.LENGTH_SHORT).show()
                                } else {
                                    searchQuery = suggestion
                                    isSearchConfirmed = true
                                    addToHistory(suggestion)
                                    focusManager.clearFocus()
                                }
                            }
                        }
                    } else {
                        if (historyList.isNotEmpty()) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Recent Searches", style = MaterialTheme.typography.titleSmall, color = Color(0xFF94A3B8))
                                    TextButton(onClick = { saveHistory(emptyList()) }) {
                                        Text("Clear All", color = Color(0xFF38BDF8))
                                    }
                                }
                            }
                            items(historyList) { item ->
                                HistoryRow(
                                    item = item,
                                    onSelect = {
                                        if (!com.example.utils.NetworkUtils.isNetworkAvailable(context)) {
                                            android.widget.Toast.makeText(context, "Offline: Please check your internet connection.", android.widget.Toast.LENGTH_SHORT).show()
                                        } else {
                                            searchQuery = item.query
                                            isSearchConfirmed = true
                                            addToHistory(item.query)
                                            focusManager.clearFocus()
                                        }
                                    },
                                    onDelete = { deleteHistoryItem(item) },
                                    onPin = { togglePin(item) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Search Results
                if (!hasAnyResults) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.SearchOff, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color(0xFF334155))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("No results found for \"$searchQuery\"", color = Color(0xFF94A3B8))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        when (selectedTab) {
                            0 -> { // All Tab
                                if (scoredBooks.isNotEmpty()) {
                                    item { ResultHeader("Books") }
                                    items(scoredBooks.take(3)) { book ->
                                        GoogleSearchCard(
                                            category = "Book",
                                            categoryIcon = Icons.Default.Book,
                                            displayPath = "Aura Learning > Books > ${book.subject}",
                                            title = book.bookName,
                                            description = book.description,
                                            thumbnail = book.coverImage,
                                            gradeText = "Class ${book.className} • ${book.subject}",
                                            onClick = {
                                                val encodedUrl = URLEncoder.encode(book.pdfUrl, "UTF-8")
                                                rootNavController.navigate("pdf_viewer?url=$encodedUrl")
                                            }
                                        )
                                    }
                                }
                                if (scoredQuestionPapers.isNotEmpty()) {
                                    item { ResultHeader("Question Papers") }
                                    items(scoredQuestionPapers.take(3)) { paper ->
                                        GoogleSearchCard(
                                            category = "Question Paper",
                                            categoryIcon = Icons.Default.Description,
                                            displayPath = "Aura Learning > Papers > ${paper.subject}",
                                            title = paper.title,
                                            description = "Previous year question paper for ${paper.board} Exam ${paper.year}. Subject: ${paper.subject}.",
                                            thumbnail = paper.thumbnail,
                                            gradeText = "${paper.board} • ${paper.year}",
                                            onClick = {
                                                val encodedUrl = URLEncoder.encode(paper.pdfUrl, "UTF-8")
                                                rootNavController.navigate("pdf_viewer?url=$encodedUrl")
                                            }
                                        )
                                    }
                                }
                                if (scoredVideos.isNotEmpty()) {
                                    item { ResultHeader("Videos") }
                                    items(scoredVideos.take(3)) { video ->
                                        GoogleSearchCard(
                                            category = "Video",
                                            categoryIcon = Icons.Default.PlayCircle,
                                            displayPath = "Aura Learning > Videos > ${video.subject}",
                                            title = video.title,
                                            description = video.description,
                                            thumbnail = video.thumbnail,
                                            gradeText = "Teacher: ${video.teacher}",
                                            onClick = { rootNavController.navigate("video_details/${video.id}") }
                                        )
                                    }
                                }
                                if (userSearchResults.isNotEmpty()) {
                                    item { ResultHeader("Users") }
                                    items(userSearchResults.take(3)) { user ->
                                        UserSearchCard(user) {
                                            rootNavController.navigate("profile_details/${user.id}")
                                        }
                                    }
                                }
                                if (scoredBoards.isNotEmpty()) {
                                    item { ResultHeader("Boards") }
                                    items(scoredBoards.take(3)) { b ->
                                        GoogleSearchCard(
                                            category = "Board Result",
                                            categoryIcon = Icons.Default.Assessment,
                                            displayPath = b.website.substringAfter("://").substringBefore("/"),
                                            title = b.board,
                                            description = "Official website to check exam results for ${b.board}.",
                                            thumbnail = null,
                                            gradeText = null,
                                            onClick = {
                                                val encUrl = URLEncoder.encode(b.website, "UTF-8")
                                                val encTitle = URLEncoder.encode(b.board, "UTF-8")
                                                rootNavController.navigate("exam_webview?url=$encUrl&title=$encTitle")
                                            }
                                        )
                                    }
                                }
                                if (scoredWebsites.isNotEmpty()) {
                                    item { ResultHeader("Websites") }
                                    items(scoredWebsites.take(3)) { w ->
                                        GoogleSearchCard(
                                            category = "Website",
                                            categoryIcon = Icons.Default.Language,
                                            displayPath = w.url.substringAfter("://").substringBefore("/"),
                                            title = w.name,
                                            description = w.description,
                                            thumbnail = w.logo,
                                            gradeText = null,
                                            onClick = {
                                                val encUrl = URLEncoder.encode(w.url, "UTF-8")
                                                val encTitle = URLEncoder.encode(w.name, "UTF-8")
                                                rootNavController.navigate("exam_webview?url=$encUrl&title=$encTitle")
                                            }
                                        )
                                    }
                                }
                                item {
                                    com.example.ui.components.NativeAdViewComposable()
                                }
                            }
                            1 -> { // Books
                                items(scoredBooks) { book ->
                                    GoogleSearchCard(
                                        category = "Book",
                                        categoryIcon = Icons.Default.Book,
                                        displayPath = "Aura Learning > Books > ${book.subject}",
                                        title = book.bookName,
                                        description = book.description,
                                        thumbnail = book.coverImage,
                                        gradeText = "Class ${book.className} • ${book.subject}",
                                        onClick = {
                                            val encodedUrl = URLEncoder.encode(book.pdfUrl, "UTF-8")
                                            rootNavController.navigate("pdf_viewer?url=$encodedUrl")
                                        }
                                    )
                                }
                            }
                            2 -> { // Question Papers
                                items(scoredQuestionPapers) { paper ->
                                    GoogleSearchCard(
                                        category = "Question Paper",
                                        categoryIcon = Icons.Default.Description,
                                        displayPath = "Aura Learning > Papers > ${paper.subject}",
                                        title = paper.title,
                                        description = "Previous year question paper for ${paper.board} Exam ${paper.year}. Subject: ${paper.subject}.",
                                        thumbnail = paper.thumbnail,
                                        gradeText = "${paper.board} • ${paper.year}",
                                        onClick = {
                                            val encodedUrl = URLEncoder.encode(paper.pdfUrl, "UTF-8")
                                            rootNavController.navigate("pdf_viewer?url=$encodedUrl")
                                        }
                                    )
                                }
                            }
                            3 -> { // Videos
                                items(scoredVideos) { video ->
                                    GoogleSearchCard(
                                        category = "Video",
                                        categoryIcon = Icons.Default.PlayCircle,
                                        displayPath = "Aura Learning > Videos > ${video.subject}",
                                        title = video.title,
                                        description = video.description,
                                        thumbnail = video.thumbnail,
                                        gradeText = "Teacher: ${video.teacher}",
                                        onClick = { rootNavController.navigate("video_details/${video.id}") }
                                    )
                                }
                            }
                            4 -> { // Users
                                items(userSearchResults) { user ->
                                    UserSearchCard(user) {
                                        rootNavController.navigate("profile_details/${user.id}")
                                    }
                                }
                            }
                            5 -> { // Results
                                items(scoredBoards) { b ->
                                    GoogleSearchCard(
                                        category = "Board Result",
                                        categoryIcon = Icons.Default.Assessment,
                                        displayPath = b.website.substringAfter("://").substringBefore("/"),
                                        title = b.board,
                                        description = "Official website to check exam results for ${b.board}.",
                                        thumbnail = null,
                                        gradeText = null,
                                        onClick = {
                                            val encUrl = URLEncoder.encode(b.website, "UTF-8")
                                            val encTitle = URLEncoder.encode(b.board, "UTF-8")
                                            rootNavController.navigate("exam_webview?url=$encUrl&title=$encTitle")
                                        }
                                    )
                                }
                                items(scoredWebsites) { w ->
                                    GoogleSearchCard(
                                        category = "Website",
                                        categoryIcon = Icons.Default.Language,
                                        displayPath = w.url.substringAfter("://").substringBefore("/"),
                                        title = w.name,
                                        description = w.description,
                                        thumbnail = w.logo,
                                        gradeText = null,
                                        onClick = {
                                            val encUrl = URLEncoder.encode(w.url, "UTF-8")
                                            val encTitle = URLEncoder.encode(w.name, "UTF-8")
                                            rootNavController.navigate("exam_webview?url=$encUrl&title=$encTitle")
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionItem(suggestion: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(suggestion, color = Color(0xFFF1F5F9), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun HistoryRow(item: HistoryItem, onSelect: () -> Unit, onDelete: () -> Unit, onPin: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(item.query, color = Color(0xFFF1F5F9), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        
        IconButton(onClick = onPin) {
            Icon(
                if (item.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin,
                contentDescription = "Pin",
                tint = if (item.isPinned) Color(0xFF38BDF8) else Color(0xFF64748B),
                modifier = Modifier.size(18.dp)
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, contentDescription = "Delete", tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun ResultHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = Color.White,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun UserSearchCard(user: User, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(Color(0xFF334155), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (user.photoUrl.isNotEmpty()) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Text(user.name.take(1).uppercase(), color = Color(0xFF38BDF8), fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(user.name, color = Color.White, fontWeight = FontWeight.Bold)
                Text(user.email, color = Color(0xFF94A3B8), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
