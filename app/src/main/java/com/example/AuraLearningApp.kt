package com.example

import android.os.Build
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.messaging.FirebaseMessaging
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.remember
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import com.example.ui.ViewModelFactory
import com.example.ui.questionPapers.QuestionPaperListingScreen
import com.example.ui.auth.AuthViewModel
import com.example.ui.auth.LoginScreen
import com.example.ui.auth.RegisterScreen
import com.example.ui.books.BooksScreen
import com.example.ui.home.HomeScreen
import com.example.ui.profile.ProfileScreen
import com.example.ui.videos.VideosScreen
import com.example.ui.theme.ThemeViewModel
import com.example.ui.gamification.LeaderboardScreen
import com.example.ui.gamification.LeaderboardViewModel
import com.example.ui.components.ErrorBoundary

import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Filled.Home)
    object QuestionPapers : Screen("questionPapers", "QuestionPapers", Icons.Filled.MenuBook)
    object Videos : Screen("videos", "Videos", Icons.Filled.PlayCircle)
    object Books : Screen("books", "Books", Icons.Outlined.Book)
    object Chat : Screen("chat_list", "Chat", Icons.Filled.Chat)
    object Profile : Screen("profile", "Profile", Icons.Filled.Person)
}

val items = listOf(
    Screen.Home,
    Screen.QuestionPapers,
    Screen.Videos,
    Screen.Books,
    Screen.Profile
)

@Composable
fun AuraLearningApp(themeViewModel: ThemeViewModel? = null, initialDeepLink: String? = null) {
    val rootNavController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel(factory = ViewModelFactory)
    val currentUser by authViewModel.currentUser.collectAsState(initial = null)
    val authState by authViewModel.authState.collectAsState()

    // Notification permission for Android 13+
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                Log.d("AuraApp", "Notification permission granted")
            } else {
                Log.w("AuraApp", "Notification permission denied")
            }
        }
        
        androidx.compose.runtime.LaunchedEffect(Unit) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    androidx.compose.runtime.LaunchedEffect(initialDeepLink) {
        initialDeepLink?.let { link ->
            try {
                rootNavController.navigate(link)
            } catch (e: Exception) {
                // Ignore bad deep links
            }
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current
    androidx.compose.runtime.DisposableEffect(context) {
        val activity = context as? androidx.activity.ComponentActivity
        val listener = androidx.core.util.Consumer<android.content.Intent> { intent ->
            val intentData = intent.data
            var newDeepLink = intent.getStringExtra("deep_link")
            if (newDeepLink.isNullOrBlank() && intent.action == "com.example.ACTION_GLOBAL_SEARCH") {
                val query = intent.getStringExtra("query") ?: ""
                newDeepLink = if (query.isNotBlank()) "global_search?query=${android.net.Uri.encode(query)}" else "global_search"
            }
            if (intentData != null && (intentData.host == "auralearningwebsite.netlify.app" || intentData.host == "aura.auralearning.workers.dev")) {
                val path = intentData.path
                val bookParam = intentData.getQueryParameter("book")
                newDeepLink = when {
                    bookParam != null -> "deeplink_loader?type=book&slug=${android.net.Uri.encode(bookParam)}"
                    path?.startsWith("/questionPaper/") == true -> {
                        val slug = path.substringAfter("/questionPaper/")
                        "deeplink_loader?type=questionPaper&slug=${android.net.Uri.encode(slug)}"
                    }
                    path?.startsWith("/video/") == true -> {
                        val slug = path.substringAfter("/video/")
                        "deeplink_loader?type=video&slug=${android.net.Uri.encode(slug)}"
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
                    path == "/questionPapers" || path?.startsWith("/questionPapers") == true -> "questionPapers"
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
            newDeepLink?.let { link ->
                try {
                    rootNavController.navigate(link)
                } catch (e: Exception) {
                    // Ignore bad deep links
                }
            }
        }
        activity?.addOnNewIntentListener(listener)
        onDispose {
            activity?.removeOnNewIntentListener(listener)
        }
    }

    NavHost(
        navController = rootNavController,
        startDestination = "splash",
        enterTransition = { fadeIn(animationSpec = tween(500)) },
        exitTransition = { fadeOut(animationSpec = tween(500)) }
    ) {
        composable("splash") { com.example.ui.splash.SplashScreen(rootNavController) }
        composable("login") { LoginScreen(rootNavController, authViewModel) }
        composable("register") { RegisterScreen(rootNavController, authViewModel) }
        composable("admin_dashboard") { com.example.ui.admin.AdminDashboardScreen(rootNavController, authViewModel) }
        composable("admin_users") { com.example.ui.admin.AdminUsersScreen(rootNavController) }
        composable(
            "admin_user_profile/{userId}",
            arguments = listOf(androidx.navigation.navArgument("userId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId") ?: ""
            com.example.ui.admin.AdminUserProfileScreen(rootNavController, userId)
        }
        composable("admin_manage_exams") { com.example.ui.admin.AdminManageExamsScreen(rootNavController) }
        composable("admin_manage_quizzes") {
            com.example.ui.admin.AdminManageQuizzesScreen(rootNavController)
        }
        composable(
            "admin_add_edit_quiz/{quizId}",
            arguments = listOf(androidx.navigation.navArgument("quizId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val quizId = backStackEntry.arguments?.getString("quizId") ?: ""
            com.example.ui.admin.AdminAddEditQuizScreen(rootNavController, quizId)
        }
        composable("admin_manage_sections") { com.example.ui.admin.AdminManageSectionsScreen(rootNavController) }
        composable("admin_notifications") { com.example.ui.admin.AdminNotificationsScreen(rootNavController) }
        composable("admin_upload_questionPaper") { com.example.ui.admin.AdminQuestionPaperUploadScreen(rootNavController) }
        composable("admin_upload_websites") { com.example.ui.admin.AdminWebsiteUploadScreen(rootNavController) }
        composable("admin_home_customization") { com.example.ui.admin.AdminHomeCustomizationScreen(rootNavController) }
        composable("admin_add_banner") { com.example.ui.admin.AdminAddEditBannerScreen(rootNavController) }
        composable(
            "admin_edit_banner/{bannerId}",
            arguments = listOf(androidx.navigation.navArgument("bannerId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("bannerId") ?: ""
            com.example.ui.admin.AdminAddEditBannerScreen(rootNavController, bannerId = id)
        }
        composable(
            "admin_upload/{type}",
            arguments = listOf(androidx.navigation.navArgument("type") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: "book"
            com.example.ui.admin.AdminContentUploadScreen(rootNavController, isVideo = type == "video")
        }
        composable(
            "admin_edit_book/{bookId}",
            arguments = listOf(androidx.navigation.navArgument("bookId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("bookId") ?: ""
            com.example.ui.admin.AdminEditContentScreen(rootNavController, id = id, isVideo = false)
        }
        composable(
            "admin_edit_video/{videoId}",
            arguments = listOf(androidx.navigation.navArgument("videoId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("videoId") ?: ""
            com.example.ui.admin.AdminEditContentScreen(rootNavController, id = id, isVideo = true)
        }
        composable(
            "admin_edit_questionPaper/{questionPaperId}",
            arguments = listOf(androidx.navigation.navArgument("questionPaperId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("questionPaperId") ?: ""
            com.example.ui.admin.AdminEditQuestionPaperScreen(rootNavController, questionPaperId = id)
        }
        composable(
            "admin_edit_website/{websiteId}",
            arguments = listOf(androidx.navigation.navArgument("websiteId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("websiteId") ?: ""
            com.example.ui.admin.AdminEditWebsiteScreen(rootNavController, websiteId = id)
        }
        composable("exam_results") { com.example.ui.profile.ExamResultScreen(rootNavController, rootNavController) }
        composable(
            "video_details/{videoId}",
            arguments = listOf(androidx.navigation.navArgument("videoId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString("videoId") ?: ""
            com.example.ui.videos.VideoDetailsScreen(id, rootNavController)
        }
        composable(
            "exam_webview?url={url}&title={title}",
            arguments = listOf(
                androidx.navigation.navArgument("url") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("title") { type = androidx.navigation.NavType.StringType }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            val title = backStackEntry.arguments?.getString("title") ?: "Result"
            com.example.ui.profile.ResultWebViewScreen(navController = rootNavController, url = url, title = title)
        }
        composable(
            "pdf_viewer?url={url}&page={page}",
            arguments = listOf(
                androidx.navigation.navArgument("url") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("page") { type = androidx.navigation.NavType.IntType; defaultValue = -1 }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            val bookId = url.hashCode().toString()
            val page = backStackEntry.arguments?.getInt("page") ?: -1
            com.example.ui.books.PdfViewerScreen(
                navController = rootNavController,
                pdfUrl = url,
                bookId = bookId,
                initialPageArg = page
            )
        }
        composable(
            "book_summary?url={url}&bookId={bookId}",
            arguments = listOf(
                androidx.navigation.navArgument("url") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("bookId") { type = androidx.navigation.NavType.StringType }
            )
        ) { backStackEntry ->
            val url = backStackEntry.arguments?.getString("url") ?: ""
            val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
            com.example.ui.books.BookSummaryScreen(navController = rootNavController, pdfUrl = url, bookId = bookId)
        }
        composable(
            "book_detail/{bookId}",
            arguments = listOf(androidx.navigation.navArgument("bookId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val bookId = backStackEntry.arguments?.getString("bookId") ?: ""
            com.example.ui.books.BookDetailScreen(
                navController = rootNavController,
                bookId = bookId,
                authViewModel = authViewModel
            )
        }
        composable(
            "video_player/{videoId}",
            arguments = listOf(androidx.navigation.navArgument("videoId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val videoId = backStackEntry.arguments?.getString("videoId") ?: ""
            com.example.ui.videos.VideoPlayerScreen(navController = rootNavController, videoId = videoId, authViewModel = authViewModel)
        }
        composable(
            "flashcards/{deckId}",
            arguments = listOf(androidx.navigation.navArgument("deckId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val deckId = backStackEntry.arguments?.getString("deckId") ?: ""
            com.example.ui.study.FlashcardsScreen(navController = rootNavController, deckId = deckId)
        }
        composable("study_planner") { com.example.ui.study.planner.StudyPlannerScreen(rootNavController) }
        composable("exam_countdown") { com.example.ui.study.ExamCountdownScreen(rootNavController) }
        composable("create_schedule") { com.example.ui.study.planner.CreateScheduleScreen(rootNavController) }
        composable("planner_settings") { com.example.ui.study.planner.PlannerSettingsScreen(rootNavController) }
        composable("notes_translate") { com.example.ui.study.NotesTranslateScreen(rootNavController) }
        composable("calculator") { com.example.ui.study.calculator.ScientificCalculatorScreen(rootNavController) }
        composable("result_analysis") { com.example.ui.study.ResultAnalysisScreen(rootNavController) }
        composable("website_reader") { com.example.ui.study.websitereader.WebsiteReaderScreen(rootNavController) }
        composable("progress") { com.example.ui.study.ProgressTrackerScreen(rootNavController) }
        composable("weekly_report") { com.example.ui.study.WeeklyReportScreen(rootNavController) }
        composable(
            "ai_chat?prompt={prompt}",
            arguments = listOf(
                androidx.navigation.navArgument("prompt") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val prompt = backStackEntry.arguments?.getString("prompt")
            com.example.ui.chat.PuterChatScreen(rootNavController, prompt)
        }
        composable("quiz/{lessonId}",
            arguments = listOf(androidx.navigation.navArgument("lessonId") { type = androidx.navigation.NavType.StringType })
        ) { backStackEntry ->
            val lessonId = backStackEntry.arguments?.getString("lessonId") ?: ""
            com.example.ui.quiz.QuizScreen(navController = rootNavController, lessonId = lessonId)
        }
        composable("leaderboard") {
            LeaderboardScreen(rootNavController)
        }
        composable("user_search") {
            com.example.ui.search.UserSearchScreen(rootNavController)
        }
        composable(
            "global_search?query={query}",
            arguments = listOf(androidx.navigation.navArgument("query") { defaultValue = "" })
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query") ?: ""
            com.example.ui.home.GlobalSearchScreen(
                navController = rootNavController,
                rootNavController = rootNavController,
                initialQuery = query
            )
        }
        composable(
            "tool_viewer/{toolId}?title={title}",
            arguments = listOf(
                androidx.navigation.navArgument("toolId") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("title") { type = androidx.navigation.NavType.StringType; defaultValue = "Study Tool" }
            )
        ) { backStackEntry ->
            val toolId = backStackEntry.arguments?.getString("toolId") ?: ""
            val title = backStackEntry.arguments?.getString("title") ?: "Study Tool"
            com.example.ui.study.ToolViewerScreen(navController = rootNavController, toolId = toolId, title = title)
        }
        composable(
            "main?tab={tab}",
            arguments = listOf(
                androidx.navigation.navArgument("tab") {
                    type = androidx.navigation.NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val tab = backStackEntry.arguments?.getString("tab")
            MainScreen(
                authViewModel = authViewModel,
                rootNavController = rootNavController,
                themeViewModel = themeViewModel,
                initialTab = tab
            )
        }
        composable("profile_settings") { com.example.ui.profile.settings.ProfileSettingsScreen(rootNavController, authViewModel, themeViewModel) }
        composable(
            "deeplink_loader?type={type}&slug={slug}",
            arguments = listOf(
                androidx.navigation.navArgument("type") { type = androidx.navigation.NavType.StringType },
                androidx.navigation.navArgument("slug") { type = androidx.navigation.NavType.StringType }
            )
        ) { backStackEntry ->
            val type = backStackEntry.arguments?.getString("type") ?: ""
            val slug = backStackEntry.arguments?.getString("slug") ?: ""
            com.example.ui.home.DeepLinkLoaderScreen(navController = rootNavController, type = type, slug = slug)
        }
        composable("profile_details") {
            com.example.ui.profile.ProfileDetailsScreen(rootNavController, authViewModel, null)
        }
        composable(
            "profile_details/{userId}",
            arguments = listOf(
                androidx.navigation.navArgument("userId") { type = androidx.navigation.NavType.StringType }
            )
        ) { backStackEntry ->
            val userId = backStackEntry.arguments?.getString("userId")
            com.example.ui.profile.ProfileDetailsScreen(rootNavController, authViewModel, userId)
        }
        composable("edit_profile") { com.example.ui.profile.settings.ProfileEditScreen(rootNavController, authViewModel) }
        composable("about_app") { com.example.ui.profile.settings.AboutAppScreen(rootNavController) }
        composable("privacy_policy") { com.example.ui.profile.settings.LegalScreen(rootNavController, "Privacy Policy") }
        composable("terms_of_use") { com.example.ui.profile.settings.LegalScreen(rootNavController, "Terms of Use") }
        composable("notifications") { com.example.ui.notifications.NotificationCenterScreen(rootNavController) }
        composable("notification_settings") { com.example.ui.notifications.NotificationSettingsScreen(rootNavController) }
        composable("my_library") { com.example.ui.profile.MyLibraryScreen(rootNavController, authViewModel) }

        composable("feedback") { com.example.ui.feedback.FeedbackScreen(rootNavController, authViewModel) }
        composable("admin_feedback_management") { com.example.ui.admin.AdminFeedbackManagementScreen(rootNavController) }
        composable("admin_feedback_analytics") { com.example.ui.admin.AdminFeedbackAnalyticsScreen(rootNavController) }

        composable("pdf_tool") { com.example.ui.pdf.screens.PdfToolScreen(rootNavController) }
        composable("pdf_builder") { com.example.ui.pdf.screens.PdfBuilderScreen(rootNavController) }
        composable("images_to_pdf") { com.example.ui.pdf.screens.ImageToPdfScreen() }
        composable("map_agent") { com.example.ui.study.map.MapAgentScreen(rootNavController) }
        composable("questionPapers") { QuestionPaperListingScreen(rootNavController) }
        composable(
            "tools",
            enterTransition = {
                slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
            },
            exitTransition = {
                slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
            },
            popEnterTransition = {
                slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(400)) + fadeIn(animationSpec = tween(400))
            },
            popExitTransition = {
                slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(400)) + fadeOut(animationSpec = tween(400))
            }
        ) {
            com.example.ui.study.ToolsScreen(rootNavController)
        }
    }
}

@Composable
fun MainScreen(
    authViewModel: AuthViewModel,
    rootNavController: androidx.navigation.NavController,
    themeViewModel: ThemeViewModel? = null,
    initialTab: String? = null
) {
    val navController = rememberNavController()

    androidx.compose.runtime.LaunchedEffect(initialTab) {
        if (initialTab != null) {
            navController.navigate(initialTab) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }
    
    val requestPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // Permission granted
        } else {
            // Explain to the user that the feature is unavailable
        }
    }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    var profileTaps by remember { androidx.compose.runtime.mutableStateOf(0) }
    var lastProfileTapTime by remember { androidx.compose.runtime.mutableStateOf(0L) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route

            val isChatRelated = currentRoute == "chat_list" ||
                currentRoute?.startsWith("chat_room") == true ||
                currentRoute?.startsWith("chat_details") == true ||
                currentRoute?.startsWith("media_viewer") == true ||
                currentRoute?.startsWith("profile_details") == true

            if (currentRoute == null || (!currentRoute.startsWith("global_search") && !isChatRelated)) {
                // Using Box to ensure the bottom bar floats correctly and allows content behind
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .navigationBarsPadding() // Float above system navigation
                ) {
                    NavigationBar(
                        modifier = Modifier
                            .shadow(16.dp, RoundedCornerShape(32.dp))
                            .clip(RoundedCornerShape(32.dp)),
                        windowInsets = WindowInsets(0, 0, 0, 0)
                    ) {
                        items.forEach { screen ->
                            NavigationBarItem(
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = { Text(screen.title) },
                                selected = currentRoute == screen.route,
                                onClick = {
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        ErrorBoundary(
            onGoHome = {
                navController.navigate(Screen.Home.route) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        ) {
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            ) {
                composable(Screen.Home.route) { HomeScreen(navController, authViewModel, rootNavController) }
                composable(
                    "global_search?query={query}",
                    arguments = listOf(androidx.navigation.navArgument("query") { defaultValue = "" })
                ) { backStackEntry ->
                    val query = backStackEntry.arguments?.getString("query") ?: ""
                    com.example.ui.home.GlobalSearchScreen(navController, rootNavController, initialQuery = query)
                }
                composable(Screen.Videos.route) { VideosScreen(navController, rootNavController) }
                composable(Screen.QuestionPapers.route) { QuestionPaperListingScreen(navController) }
                composable(Screen.Books.route) { BooksScreen(navController, authViewModel, rootNavController) }
                composable("resources") { com.example.ui.home.ResourcesScreen(navController, rootNavController) }
                composable(Screen.Chat.route) { com.example.ui.chat.ChatListScreen(navController) }
                composable(
                    "chat_room/{conversationId}",
                    arguments = listOf(androidx.navigation.navArgument("conversationId") { type = androidx.navigation.NavType.StringType })
                ) { backStackEntry -> 
                    val id = backStackEntry.arguments?.getString("conversationId") ?: return@composable
                    com.example.ui.chat.ChatRoomScreen(navController, id) 
                }
                composable(
                    "chat_details/{conversationId}",
                    arguments = listOf(androidx.navigation.navArgument("conversationId") { type = androidx.navigation.NavType.StringType })
                ) { backStackEntry ->
                    val id = backStackEntry.arguments?.getString("conversationId") ?: ""
                    com.example.ui.chat.ChatDetailsScreen(navController, id)
                }
                composable(
                    "media_viewer?url={url}",
                    arguments = listOf(androidx.navigation.navArgument("url") { type = androidx.navigation.NavType.StringType; defaultValue = "" })
                ) { backStackEntry ->
                    val url = backStackEntry.arguments?.getString("url") ?: ""
                    com.example.ui.chat.MediaViewerScreen(navController, url)
                }
                composable(
                    "profile_details/{userId}",
                    arguments = listOf(androidx.navigation.navArgument("userId") { type = androidx.navigation.NavType.StringType; defaultValue = "" })
                ) { backStackEntry ->
                    val userId = backStackEntry.arguments?.getString("userId") ?: ""
                    com.example.ui.profile.ProfileDetailsScreen(rootNavController, authViewModel, userId)
                }
                composable("profile_details") {
                    com.example.ui.profile.ProfileDetailsScreen(rootNavController, authViewModel, null)
                }
                composable(Screen.Profile.route) { ProfileScreen(navController, authViewModel, rootNavController, themeViewModel) }
            }
        }
    }
}

