package com.example.ui.home

import android.content.Intent
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import com.example.data.repository.notifications.NotificationRepository
import com.example.ui.ViewModelFactory
import kotlinx.coroutines.launch
import com.example.ui.auth.AuthViewModel
import com.example.data.models.Book
import com.example.data.models.Video
import com.example.data.models.User
import com.example.data.models.QuestionPaper
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.delay
import com.example.data.models.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    authViewModel: AuthViewModel,
    rootNavController: NavController,
    viewModel: HomeViewModel = viewModel(factory = ViewModelFactory)
) {
    val banners by viewModel.banners.collectAsState()
    val homeSections by viewModel.homeSections.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val announcements by viewModel.announcements.collectAsState()
    
    val recentBooks by viewModel.recentBooks.collectAsState()
    val recentVideos by viewModel.recentVideos.collectAsState()
    val allQuestionPapers by viewModel.allQuestionPapers.collectAsState()
    val allWebsites by viewModel.allWebsites.collectAsState()
    
    val currentUser by authViewModel.currentUser.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    val notificationRepository = remember { NotificationRepository(context) }
    val unreadCount by notificationRepository.getUnreadCount().collectAsState(initial = 0)

    LaunchedEffect(Unit) {
        viewModel.fetchData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { },
                actions = {
                    IconButton(onClick = { rootNavController.navigate("notifications") }) {
                        BadgedBox(
                            badge = {
                                if (unreadCount > 0) {
                                    Badge {
                                        Text(unreadCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                    }
                    IconButton(onClick = { navController.navigate("chat_list") }) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Chat")
                    }
                    IconButton(onClick = { rootNavController.navigate("profile_details") }) {
                        Icon(Icons.Filled.AccountCircle, contentDescription = "Profile")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        val errorMessage by viewModel.errorMessage.collectAsState()

        PullToRefreshBox(
            isRefreshing = isLoading,
            onRefresh = { viewModel.fetchData() },
            modifier = Modifier.padding(padding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
            ) {
                // 1. Global Search Bar
                SearchBarSection(onSearchClick = { navController.navigate("global_search") })
                
                Spacer(modifier = Modifier.height(16.dp))

                // Error message banner/card if there's any error
                errorMessage?.let { error ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ErrorOutline,
                                    contentDescription = "Error",
                                    tint = MaterialTheme.colorScheme.error
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = error,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { viewModel.fetchData() },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                                modifier = Modifier.height(48.dp) // Accessibility min 48dp height
                            ) {
                                Text("Retry", color = MaterialTheme.colorScheme.onError)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // 2. Quick Actions
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    QuickActionButton(
                        text = "Google Search",
                        icon = null,
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val encodedUrl = android.net.Uri.encode("https://www.google.com")
                            rootNavController.navigate("exam_webview?url=$encodedUrl&title=Search")
                        },
                        isGoogle = true
                    )
                    QuickActionButton(
                        text = "AI Mode",
                        icon = Icons.Default.AutoAwesome,
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            val encodedUrl = android.net.Uri.encode("https://www.google.com/search?udm=50")
                            rootNavController.navigate("exam_webview?url=$encodedUrl&title=AI Mode")
                        }
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. Banner Carousel
                if (banners.isNotEmpty()) {
                    BannerCarousel(
                        banners = banners,
                        navController = navController,
                        rootNavController = rootNavController
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                } else if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp).padding(horizontal = 16.dp)) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                // 4. Dynamic Sections
                homeSections.filter { it.isVisible }.forEachIndexed { index, section ->
                    when (section.type) {
                        "home" -> {
                            DefaultNavigationSectionsSection(
                                navController = navController,
                                scrollState = scrollState
                            )
                        }
                        "books" -> {
                            ContentSection(
                                title = section.title,
                                icon = section.icon,
                                items = recentBooks,
                                onViewAll = { navController.navigate("books") }
                            ) { book ->
                                BookCard(book = book) {
                                    rootNavController.navigate("book_detail/${book.id}")
                                }
                            }
                        }
                        "videos" -> {
                            ContentSection(
                                title = section.title,
                                icon = section.icon,
                                items = recentVideos,
                                onViewAll = { navController.navigate("videos") }
                            ) { video ->
                                VideoCard(video = video) {
                                    rootNavController.navigate("video_details/${video.id}")
                                }
                            }
                        }
                        "question_papers" -> {
                            ContentSection(
                                title = section.title,
                                icon = section.icon,
                                items = allQuestionPapers,
                                onViewAll = { navController.navigate("questionPapers") }
                            ) { questionPaper ->
                                QuestionPaperCard(questionPaper = questionPaper) {
                                    val encUrl = android.net.Uri.encode(questionPaper.pdfUrl)
                                    val encTitle = android.net.Uri.encode(questionPaper.title)
                                    rootNavController.navigate("exam_webview?url=$encUrl&title=$encTitle")
                                }
                            }
                        }
                        "websites" -> {
                            ContentSection(
                                title = section.title,
                                icon = section.icon,
                                items = allWebsites,
                                onViewAll = { /* View all websites */ }
                            ) { website ->
                                WebsiteCard(website = website) {
                                    val encodedUrl = android.net.Uri.encode(website.url)
                                    rootNavController.navigate("exam_webview?url=$encodedUrl&title=${website.name}")
                                }
                            }
                        }
                        "exams" -> {
                            ExamSection(
                                title = section.title,
                                icon = section.icon,
                                navController = navController
                            )
                        }
                        "announcements" -> {
                            AnnouncementSection(
                                title = section.title,
                                icon = section.icon,
                                announcements = announcements
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    if (index == 1 || (index > 1 && index % 3 == 0)) {
                        com.example.ui.components.NativeAdViewComposable()
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun QuickActionButton(
    text: String,
    icon: ImageVector?,
    containerColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    isGoogle: Boolean = false
) {
    Card(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (isGoogle) {
                GoogleIcon(modifier = Modifier.size(18.dp))
            } else if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp), tint = Color(0xFF8B5CF6))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BannerCarousel(
    banners: List<Banner>,
    navController: NavController,
    rootNavController: NavController
) {
    var currentPage by remember { mutableIntStateOf(0) }
    
    LaunchedEffect(banners) {
        while (true) {
            delay(5000)
            if (banners.isNotEmpty()) {
                currentPage = (currentPage + 1) % banners.size
            }
        }
    }

    if (banners.isEmpty()) return

    val banner = banners[currentPage]
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AsyncImage(
                model = banner.imageUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            // Gradient Overlay
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    banner.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    banner.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = { 
                        if (banner.link.isNotEmpty()) {
                            if (banner.link.startsWith("http")) {
                                val encodedUrl = android.net.Uri.encode(banner.link)
                                rootNavController.navigate("exam_webview?url=$encodedUrl&title=${banner.title}")
                            } else {
                                navController.navigate(banner.link)
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(banner.ctaText, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun <T> ContentSection(
    title: String,
    icon: String,
    items: List<T>,
    onViewAll: () -> Unit,
    content: @Composable (T) -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionIcon(icon)
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            TextButton(onClick = onViewAll) {
                Text("View All")
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(items) { item ->
                content(item)
            }
        }
    }
}

@Composable
fun WebsiteCard(website: com.example.data.models.Website, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AsyncImage(
                model = website.logo,
                contentDescription = null,
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                website.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = onClick,
                modifier = Modifier.fillMaxWidth().height(32.dp),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("Open", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
fun ExamSection(title: String, icon: String, navController: NavController) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionIcon(icon)
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { ExamActionCard("Quiz", Icons.Default.Quiz, Color(0xFFE3F2FD)) { /* Navigate */ } }
            item { ExamActionCard("Mock Test", Icons.Default.Assignment, Color(0xFFF3E5F5)) { /* Navigate */ } }
            item { ExamActionCard("PYP", Icons.Default.History, Color(0xFFFFF3E0)) { /* Navigate */ } }
            item { ExamActionCard("Notes", Icons.Default.NoteAlt, Color(0xFFE8F5E9)) { /* Navigate */ } }
        }
    }
}

@Composable
fun ExamActionCard(title: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(110.dp)
            .height(90.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = Color.Black.copy(alpha = 0.6f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(title, style = MaterialTheme.typography.labelMedium, color = Color.Black, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun AnnouncementSection(title: String, icon: String, announcements: List<com.example.data.models.Announcement>) {
    if (announcements.isEmpty()) return
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionIcon(icon)
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        Column(
            modifier = Modifier.padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            announcements.take(3).forEach { announcement ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f))
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(announcement.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(announcement.content, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun QuestionPaperCard(questionPaper: com.example.data.models.QuestionPaper, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            AsyncImage(
                model = questionPaper.thumbnail,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(12.dp)) {
                Text(questionPaper.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(questionPaper.subject, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun SearchBarSection(onSearchClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        onClick = onSearchClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                "Search books, videos, question papers, websites, exams...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
@Composable
fun BookCard(book: Book, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            AsyncImage(
                model = book.coverImage,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentScale = ContentScale.Crop
            )
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = book.bookName,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = book.subject,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun VideoCard(video: Video, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .width(220.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Box {
                AsyncImage(
                    model = video.thumbnail,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentScale = ContentScale.Crop
                )
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = video.duration,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.Center),
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = video.teacher,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun GoogleIcon(modifier: Modifier = Modifier.size(24.dp)) {
    Canvas(modifier = modifier) {
        val sizePx = size.minDimension
        val strokeWidth = sizePx * 0.18f
        val radius = (sizePx - strokeWidth) / 2f
        val arcSize = sizePx - strokeWidth
        val centerPoint = center
        
        // Red segment (top)
        drawArc(
            color = Color(0xFFEA4335),
            startAngle = 180f,
            sweepAngle = 100f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Square),
            topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = androidx.compose.ui.geometry.Size(arcSize, arcSize)
        )
        // Yellow segment (bottom-left)
        drawArc(
            color = Color(0xFFFBBC05),
            startAngle = 100f,
            sweepAngle = 80f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Square),
            topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = androidx.compose.ui.geometry.Size(arcSize, arcSize)
        )
        // Green segment (bottom)
        drawArc(
            color = Color(0xFF34A853),
            startAngle = 0f,
            sweepAngle = 100f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Square),
            topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = androidx.compose.ui.geometry.Size(arcSize, arcSize)
        )
        // Blue segment (right & horizontal line)
        drawArc(
            color = Color(0xFF4285F4),
            startAngle = 280f,
            sweepAngle = 80f,
            useCenter = false,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Square),
            topLeft = androidx.compose.ui.geometry.Offset(strokeWidth / 2f, strokeWidth / 2f),
            size = androidx.compose.ui.geometry.Size(arcSize, arcSize)
        )
        
        // Horizontal bar of "G"
        val barLength = radius * 0.95f
        drawLine(
            color = Color(0xFF4285F4),
            start = androidx.compose.ui.geometry.Offset(centerPoint.x, centerPoint.y),
            end = androidx.compose.ui.geometry.Offset(centerPoint.x + barLength, centerPoint.y),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Square
        )
    }
}

@Composable
fun SectionIcon(iconStr: String) {
    when (iconStr) {
        "home" -> Icon(Icons.Default.Home, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        "book" -> Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        "play_circle" -> Icon(Icons.Default.PlayCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        "question_papers", "graduation_cap" -> Icon(Icons.Default.School, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
        else -> {
            val displayIcon = when (iconStr) {
                "📚" -> "📚"
                "🎥" -> "🎥"
                "🎓" -> "🎓"
                "🌐" -> "🌐"
                "📝" -> "📝"
                "🔥" -> "🔥"
                "⭐" -> "⭐"
                "📢" -> "📢"
                else -> iconStr
            }
            if (displayIcon.length <= 2) {
                Text(displayIcon, style = MaterialTheme.typography.titleLarge)
            } else {
                Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            }
        }
    }
}

@Composable
fun DefaultNavigationSectionsSection(
    navController: NavController,
    scrollState: androidx.compose.foundation.ScrollState
) {
    val coroutineScope = rememberCoroutineScope()
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Explore, 
                contentDescription = null, 
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Main Navigation", 
                style = MaterialTheme.typography.titleLarge, 
                fontWeight = FontWeight.Bold
            )
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Card 1: Home
            NavigationSectionCard(
                title = "Home",
                icon = Icons.Default.Home,
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
                onClick = {
                    coroutineScope.launch {
                        scrollState.animateScrollTo(0)
                    }
                }
            )
            
            // Card 2: Books
            NavigationSectionCard(
                title = "Books",
                icon = Icons.Default.Book,
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.weight(1f),
                onClick = {
                    navController.navigate("books")
                }
            )
            
            // Card 3: Videos
            NavigationSectionCard(
                title = "Videos",
                icon = Icons.Default.PlayCircle,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.2f),
                contentColor = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f),
                onClick = {
                    navController.navigate("videos")
                }
            )
        }
    }
}

@Composable
fun NavigationSectionCard(
    title: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(90.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, contentColor.copy(alpha = 0.15f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = contentColor,
                modifier = Modifier.size(28.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

