package com.example.ui.videos

import android.content.Intent
import android.net.Uri
import android.content.ActivityNotFoundException
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.example.data.local.PlannerDatabase
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.ui.ViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPlayerScreen(
    navController: NavController,
    videoId: String,
    authViewModel: com.example.ui.auth.AuthViewModel,
    viewModel: VideoPlayerViewModel = viewModel(factory = ViewModelFactory)
) {
    val video by viewModel.video.collectAsState()
    val chapterVideos by viewModel.chapterVideos.collectAsState()
    val relatedBooks by viewModel.relatedBooks.collectAsState()
    val suggestedVideos by viewModel.suggestedVideos.collectAsState()

    val currentUser by authViewModel.currentUser.collectAsState()
    val isSaved = currentUser?.savedVideos?.contains(videoId) == true

    var showAllParts by remember { mutableStateOf(false) }
    var showNoteDialog by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val noteDao = PlannerDatabase.getDatabase(context).noteDao()
    val noteViewModel: com.example.ui.notes.NoteTakingViewModel = viewModel(
        factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                return com.example.ui.notes.NoteTakingViewModel(noteDao) as T
            }
        }
    )

    LaunchedEffect(videoId) {
        viewModel.loadVideo(videoId)
    }

    if (showAllParts) {
        ModalBottomSheet(onDismissRequest = { showAllParts = false }) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("All Parts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(chapterVideos) { part ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showAllParts = false
                                    viewModel.loadVideo(part.id)
                                }
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = part.thumbnail.ifEmpty { "https://images.unsplash.com/photo-1596496050827-8299e0220de1?auto=format&fit=crop&w=300&q=80" },
                                contentDescription = null,
                                modifier = Modifier
                                    .size(100.dp, 60.dp),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text("Part ${part.partNumber}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                Text(part.title, style = MaterialTheme.typography.bodyMedium, fontWeight = if (part.id == video?.id) FontWeight.Bold else FontWeight.Normal)
                            }
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(video?.title ?: "Loading...") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    video?.let { v ->
                        IconButton(onClick = {
                            com.example.utils.ShareHelper.shareContent(
                                context = context,
                                title = v.title,
                                contentType = "video",
                                id = v.id
                            )
                        }) {
                            Icon(imageVector = androidx.compose.material.icons.Icons.Filled.Share, contentDescription = "Share Video")
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            com.example.ui.notes.FloatingNoteButton(onNoteClick = { showNoteDialog = true })
        }
    ) { padding ->
        if (video == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Video Player
                item {
                    val context = LocalContext.current
                    
                    Column(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        com.example.ui.components.YouTubePlayerComponent(
                            videoId = video?.youtubeVideoId,
                            videoUrl = video?.videoUrl,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f)
                        )
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Having trouble playing?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    com.example.ui.components.openInYouTubeAppOrBrowser(
                                        context = context,
                                        fullUrl = video?.videoUrl ?: "",
                                        videoId = video?.youtubeVideoId
                                    )
                                }
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open in YouTube App / Browser", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                // Video Details
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(video!!.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            IconButton(onClick = { 
                                authViewModel.toggleSaveVideo(videoId)
                                val msg = if (isSaved) "Removed from Learning" else "Saved to Learning"
                                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(
                                    imageVector = if (isSaved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                    contentDescription = "Save to Learning",
                                    tint = if (isSaved) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Badge { Text(video!!.subject) }
                            Badge { Text("Class ${video!!.className}") }
                            if (video!!.duration.isNotEmpty()) {
                                Badge(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer) { Text(video!!.duration) }
                            }
                        }
                        if (video!!.teacher.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("By ${video!!.teacher}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (video!!.description.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(video!!.description, style = MaterialTheme.typography.bodyMedium)
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        val isWatched by viewModel.isWatched.collectAsState()
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { viewModel.markAsWatched() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isWatched) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary
                                )
                            ) {
                                Icon(
                                    if (isWatched) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isWatched) "Watched" else "Mark as Watched")
                            }
                            
                            Button(
                                onClick = { navController.navigate("quiz/${video!!.id}") },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Filled.Bookmark, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Take Quiz")
                            }
                        }
                    }
                }

                // Chapter Parts
                if (chapterVideos.size > 1) {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Parts of this Chapter", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { showAllParts = true }) {
                                    Text("All Parts")
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            chapterVideos.forEach { part ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 8.dp)
                                        .clickable { viewModel.loadVideo(part.id) },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (part.id == video!!.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Part ${part.partNumber}", modifier = Modifier.padding(end = 16.dp, start = 8.dp), fontWeight = FontWeight.Bold)
                                        Text(part.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                        if (part.id == video!!.id) {
                                            Text("Playing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(end = 8.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Related Books
                if (relatedBooks.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(vertical = 16.dp)) {
                            Text(
                                "Related Books",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(relatedBooks) { book ->
                                    Card(
                                        modifier = Modifier
                                            .width(140.dp)
                                            .clickable { navController.navigate("book_detail/${book.id}") }
                                    ) {
                                        Column {
                                            AsyncImage(
                                                model = book.coverImage.ifEmpty { "https://images.unsplash.com/photo-1544947950-fa07a98d237f?auto=format&fit=crop&w=300&q=80" },
                                                contentDescription = book.bookName,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(180.dp),
                                                contentScale = ContentScale.Crop
                                            )
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(book.bookName, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                                                Text(book.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Suggested Videos
                if (suggestedVideos.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.padding(vertical = 16.dp)) {
                            Text(
                                "Continue Learning",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                            )
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(suggestedVideos) { v ->
                                    Card(
                                        modifier = Modifier
                                            .width(200.dp)
                                            .clickable { viewModel.loadVideo(v.id) }
                                    ) {
                                        Column {
                                            AsyncImage(
                                                model = v.thumbnail.ifEmpty { "https://images.unsplash.com/photo-1596496050827-8299e0220de1?auto=format&fit=crop&w=300&q=80" },
                                                contentDescription = v.title,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(112.dp),
                                                contentScale = ContentScale.Crop
                                            )
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(v.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                                                Spacer(modifier = Modifier.height(4.dp))
                                                Text(v.className + " • " + v.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }
        
        if (showNoteDialog) {
            com.example.ui.notes.NoteDialog(
                onDismiss = { showNoteDialog = false },
                onSave = { content -> noteViewModel.saveNote(content, "video/$videoId") }
            )
        }
    }
}
