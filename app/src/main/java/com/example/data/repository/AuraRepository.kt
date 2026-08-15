package com.example.data.repository

import com.example.AppContext
import com.example.data.models.Banner
import com.example.data.models.Book
import com.example.data.models.Flashcard
import com.example.data.models.FlashcardDeck
import com.example.data.models.Note
import com.example.data.models.QuestionPaperSection
import com.example.data.models.User
import com.example.data.models.Video
import com.example.data.models.VideoProgress
import com.example.data.models.BookProgress
import com.example.data.models.QuestionPaper
import com.example.data.supabase.SupabaseService
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

class AuraRepository {
    private val lenientJson = Json { encodeDefaults = true; ignoreUnknownKeys = true }

    private inline fun <reified T : Any> getJsonWithoutId(item: T): Map<String, kotlinx.serialization.json.JsonElement> {
        val map = lenientJson.encodeToJsonElement(item).jsonObject.toMutableMap()
        map.remove("id")
        return map
    }

    private inline fun <reified T : Any> getJsonListWithoutId(items: List<T>): List<Map<String, kotlinx.serialization.json.JsonElement>> {
        return items.map { getJsonWithoutId(it) }
    }

    private val client = SupabaseService.client
    private val prefs by lazy { AppContext.context.getSharedPreferences("guest_prefs", android.content.Context.MODE_PRIVATE) }

    companion object {
        // Shared update triggers across any instances of the repository
        private val _booksUpdateTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        val booksUpdateTrigger = _booksUpdateTrigger.asSharedFlow()

        private val _videosUpdateTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        val videosUpdateTrigger = _videosUpdateTrigger.asSharedFlow()

        private val _boardsUpdateTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        val boardsUpdateTrigger = _boardsUpdateTrigger.asSharedFlow()

        private val _notificationsUpdateTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        val notificationsUpdateTrigger = _notificationsUpdateTrigger.asSharedFlow()

        private val _sectionsUpdateTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        val sectionsUpdateTrigger = _sectionsUpdateTrigger.asSharedFlow()

        private val _homeConfigUpdateTrigger = MutableSharedFlow<Unit>(replay = 1).apply { tryEmit(Unit) }
        val homeConfigUpdateTrigger = _homeConfigUpdateTrigger.asSharedFlow()

        suspend fun notifyBooksChanged() {
            _booksUpdateTrigger.emit(Unit)
        }

        suspend fun notifyVideosChanged() {
            _videosUpdateTrigger.emit(Unit)
        }

        suspend fun notifyBoardsChanged() {
            _boardsUpdateTrigger.emit(Unit)
        }

        suspend fun notifyNotificationsChanged() {
            _notificationsUpdateTrigger.emit(Unit)
        }

        suspend fun notifySectionsChanged() {
            _sectionsUpdateTrigger.emit(Unit)
        }

        suspend fun notifyHomeConfigChanged() {
            _homeConfigUpdateTrigger.emit(Unit)
        }
    }

    fun getGuestProfile(): User {
        val savedBooks = prefs.getStringSet("savedBooks", emptySet())?.toList() ?: emptyList()
        val savedVideos = prefs.getStringSet("savedVideos", emptySet())?.toList() ?: emptyList()
        return User(
            id = "guest_user",
            name = "Guest",
            role = "guest",
            savedBooks = savedBooks,
            savedVideos = savedVideos
        )
    }

    fun saveGuestProfile(user: User) {
        prefs.edit()
            .putStringSet("savedBooks", user.savedBooks.toSet())
            .putStringSet("savedVideos", user.savedVideos.toSet())
            .apply()
    }
    
    fun clearGuestProfile() {
        prefs.edit().clear().apply()
    }

    // Users
    suspend fun getUserProfile(uid: String): User? {
        return try {
            client.postgrest["users"].select {
                filter { eq("id", uid) }
            }.decodeSingle<User>()
        } catch (e: Exception) {
            null
        }
    }

    suspend fun searchUsers(query: String): List<User> {
        return try {
            client.postgrest["users"].select {
                filter {
                    or {
                        ilike("name", "%$query%")
                        ilike("email", "%$query%")
                        ilike("id", "%$query%")
                        ilike("mobileNumber", "%$query%")
                        ilike("studentId", "%$query%") // Also adding studentId as it's common for academic apps
                    }
                }
            }.decodeList<User>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAllUsers(): List<User> {
        return try {
            client.postgrest["users"].select {
                limit(100)
            }.decodeList<User>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun deleteUser(uid: String) {
        try {
            client.postgrest["users"].delete {
                filter { eq("id", uid) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun createUserProfile(user: User) {
        try {
            client.postgrest["users"].insert(user)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun updateUserProfile(user: User) {
        try {
            client.postgrest["users"].update(user) {
                filter { eq("id", user.id) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    // Books
    private fun parseBookFromJson(jsonObj: kotlinx.serialization.json.JsonObject): Book {
        fun getString(vararg keys: String): String {
            for (k in keys) {
                val elem = jsonObj[k]
                if (elem != null && elem !is kotlinx.serialization.json.JsonNull) {
                    if (elem is kotlinx.serialization.json.JsonPrimitive) {
                        return elem.content
                    } else {
                        return elem.toString()
                    }
                }
            }
            return ""
        }

        val idStr = getString("id", "book_id")
        val bookName = getString("bookName", "book_name", "title", "name")
        val className = getString("className", "class_name", "class", "grade")
        val subject = getString("subject", "subject_name")
        val description = getString("description", "summary")
        val coverImage = getString("coverImage", "cover_image", "thumbnail", "image_url")
        val pdfUrl = getString("pdfUrl", "pdf_url", "file_url", "url")
        
        val createdAtElem = jsonObj["createdAt"] ?: jsonObj["created_at"]
        val createdAtLong = when (createdAtElem) {
            is kotlinx.serialization.json.JsonPrimitive -> {
                createdAtElem.content.toLongOrNull() ?: 0L
            }
            else -> 0L
        }

        return Book(
            id = idStr,
            bookName = bookName,
            className = className,
            subject = subject,
            description = description,
            coverImage = coverImage,
            pdfUrl = pdfUrl,
            createdAt = createdAtLong
        )
    }

    private fun getDefaultBooks(): List<Book> {
        return listOf(
            Book(
                id = "default_book_1",
                bookName = "NCERT Class 10 Mathematics",
                className = "10th",
                subject = "Mathematics",
                description = "Comprehensive Mathematics textbook for Class 10 covering Quadratic Equations, Trigonometry, and Circles.",
                coverImage = "https://images.unsplash.com/photo-1544716278-ca5e3f4abd8c?auto=format&fit=crop&w=600&q=80",
                pdfUrl = "https://ncert.nic.in/textbook/pdf/jemh111.pdf"
            ),
            Book(
                id = "default_book_2",
                bookName = "NCERT Class 10 Science",
                className = "10th",
                subject = "Science",
                description = "Class 10 Science textbook covering Physics, Chemistry, and Biology fundamentals.",
                coverImage = "https://images.unsplash.com/photo-1532012197267-da84d127e765?auto=format&fit=crop&w=600&q=80",
                pdfUrl = "https://ncert.nic.in/textbook/pdf/jesc116.pdf"
            ),
            Book(
                id = "default_book_3",
                bookName = "NCERT Class 10 Social Science - History",
                className = "10th",
                subject = "Social science",
                description = "India and the Contemporary World - II for Class 10 Social Science students.",
                coverImage = "https://images.unsplash.com/photo-1497633762265-9d179a990aa6?auto=format&fit=crop&w=600&q=80",
                pdfUrl = "https://ncert.nic.in/textbook/pdf/jess101.pdf"
            ),
            Book(
                id = "default_book_4",
                bookName = "NCERT Class 10 English First Flight",
                className = "10th",
                subject = "English",
                description = "Main English literature textbook for CBSE Class 10 containing poems and stories.",
                coverImage = "https://images.unsplash.com/photo-1512820790803-83ca734da794?auto=format&fit=crop&w=600&q=80",
                pdfUrl = "https://ncert.nic.in/textbook/pdf/jefp101.pdf"
            ),
            Book(
                id = "default_book_5",
                bookName = "NCERT Class 10 Hindi Kshitij Part 2",
                className = "10th",
                subject = "Hindi",
                description = "Class 10 Hindi textbook with rich literary selections and grammar practice.",
                coverImage = "https://images.unsplash.com/photo-1457369804613-52c61a468e7d?auto=format&fit=crop&w=600&q=80",
                pdfUrl = "https://ncert.nic.in/textbook/pdf/jhks101.pdf"
            ),
            Book(
                id = "default_book_6",
                bookName = "NCERT Class 9 Mathematics",
                className = "9th",
                subject = "Mathematics",
                description = "Class 9 Mathematics textbook covering Number Systems, Algebra, and Geometry.",
                coverImage = "https://images.unsplash.com/photo-1509062522246-3755977927d7?auto=format&fit=crop&w=600&q=80",
                pdfUrl = "https://ncert.nic.in/textbook/pdf/iemh101.pdf"
            ),
            Book(
                id = "default_book_7",
                bookName = "NCERT Class 9 Science",
                className = "9th",
                subject = "Science",
                description = "Class 9 Science textbook with foundational physics, chemistry, and biology topics.",
                coverImage = "https://images.unsplash.com/photo-1532094349884-543bc11b234d?auto=format&fit=crop&w=600&q=80",
                pdfUrl = "https://ncert.nic.in/textbook/pdf/iesc101.pdf"
            ),
            Book(
                id = "default_book_8",
                bookName = "NCERT Class 12 Physics Part 1",
                className = "12th",
                subject = "Science",
                description = "Higher Secondary Physics textbook for Class 12 covering Electrostatics and Magnetism.",
                coverImage = "https://images.unsplash.com/photo-1636466497217-26a8cbeaf0aa?auto=format&fit=crop&w=600&q=80",
                pdfUrl = "https://ncert.nic.in/textbook/pdf/leph101.pdf"
            )
        )
    }

    suspend fun getBooks(): List<Book> {
        return try {
            val jsonArray = client.postgrest["books"].select().decodeList<kotlinx.serialization.json.JsonObject>()
            val parsedBooks = jsonArray.map { parseBookFromJson(it) }.filter { it.bookName.isNotBlank() || it.pdfUrl.isNotBlank() }
            
            android.util.Log.d("AuraRepository", "Fetched ${parsedBooks.size} books from Supabase")
            
            if (parsedBooks.isNotEmpty()) {
                try {
                    val dao = com.example.data.local.PlannerDatabase.getDatabase(AppContext.context).cachedBookDao()
                    dao.insertCachedBooks(parsedBooks.map { com.example.data.local.CachedBookEntity.fromBook(it) })
                } catch (e: Exception) {
                    android.util.Log.e("AuraRepository", "Error caching books in Room: ${e.message}", e)
                }
                parsedBooks
            } else {
                val cached = try {
                    val dao = com.example.data.local.PlannerDatabase.getDatabase(AppContext.context).cachedBookDao()
                    dao.getAllCachedBooks().map { it.toBook() }
                } catch (dbE: Exception) {
                    emptyList()
                }
                android.util.Log.d("AuraRepository", "Supabase returned 0 books, loaded ${cached.size} from Room cache")
                if (cached.isNotEmpty()) cached else getDefaultBooks()
            }
        } catch (e: Exception) {
            android.util.Log.e("AuraRepository", "Error fetching books from Supabase: ${e.message}", e)
            try {
                val dao = com.example.data.local.PlannerDatabase.getDatabase(AppContext.context).cachedBookDao()
                val cached = dao.getAllCachedBooks().map { it.toBook() }
                android.util.Log.d("AuraRepository", "Error fallback, loaded ${cached.size} books from Room cache")
                if (cached.isNotEmpty()) cached else getDefaultBooks()
            } catch (dbE: Exception) {
                android.util.Log.e("AuraRepository", "Error reading Room cache: ${dbE.message}", dbE)
                getDefaultBooks()
            }
        }
    }

    suspend fun getBook(bookId: String): Book? {
        val allBooks = getBooks()
        return allBooks.find { it.id == bookId || it.id.equals(bookId, ignoreCase = true) }
    }

    suspend fun getBooksByClass(className: String): List<Book> {
        val allBooks = getBooks()
        if (className.isBlank() || className.equals("All Grades", ignoreCase = true)) return allBooks
        val targetDigits = className.filter { it.isDigit() }
        return allBooks.filter { book ->
            val bookClass = book.className.trim()
            val bookDigits = bookClass.filter { it.isDigit() }
            bookClass.equals(className, ignoreCase = true) ||
            (targetDigits.isNotEmpty() && bookDigits == targetDigits) ||
            bookClass.contains(className, ignoreCase = true) ||
            className.contains(bookClass, ignoreCase = true)
        }
    }

    suspend fun addBook(book: Book) {
        val newBook = book
        client.postgrest["books"].insert(if (newBook.id.isEmpty() || newBook.id.length > 20) getJsonWithoutId(newBook) else newBook)
        notifyBooksChanged()
    }
    
    suspend fun updateBook(book: Book) {
        if (book.id.isNotEmpty()) {
            client.postgrest["books"].update(book) {
                filter { eq("id", book.id) }
            }
            notifyBooksChanged()
        }
    }
    
    suspend fun deleteBook(bookId: String) {
        if (bookId.isNotEmpty()) {
            val idValue: Any = bookId.toLongOrNull() ?: bookId
            client.postgrest["books"].delete {
                filter { eq("id", idValue) }
            }
            notifyBooksChanged()
        }
    }

    // Videos
    suspend fun getVideos(): List<Video> {
        return try {
            val fetchedVideos = client.postgrest["videos"].select().decodeList<Video>()
            
            if (fetchedVideos.isNotEmpty()) {
                try {
                    val dao = com.example.data.local.PlannerDatabase.getDatabase(AppContext.context).cachedVideoDao()
                    dao.insertCachedVideos(fetchedVideos.map { com.example.data.local.CachedVideoEntity.fromVideo(it) })
                } catch (e: Exception) {
                    android.util.Log.e("AuraRepository", "Error caching videos in Room: ${e.message}", e)
                }
                fetchedVideos
            } else {
                val cached = try {
                    val dao = com.example.data.local.PlannerDatabase.getDatabase(AppContext.context).cachedVideoDao()
                    dao.getAllCachedVideos().map { it.toVideo() }
                } catch (dbE: Exception) {
                    emptyList()
                }
                cached
            }
        } catch (e: Exception) {
            android.util.Log.e("AuraRepository", "Error fetching videos from Supabase: ${e.message}", e)
            try {
                val dao = com.example.data.local.PlannerDatabase.getDatabase(AppContext.context).cachedVideoDao()
                dao.getAllCachedVideos().map { it.toVideo() }
            } catch (dbE: Exception) {
                android.util.Log.e("AuraRepository", "Error reading Room cache for videos: ${dbE.message}", dbE)
                emptyList()
            }
        }
    }

    suspend fun getVideosByClass(className: String): List<Video> {
        val allVideos = getVideos()
        if (className.isBlank() || className.equals("All Grades", ignoreCase = true)) return allVideos
        
        val targetDigits = className.filter { it.isDigit() }
        return allVideos.filter { video ->
            val videoClass = video.className.trim()
            val videoDigits = videoClass.filter { it.isDigit() }
            
            videoClass.equals(className, ignoreCase = true) ||
            (targetDigits.isNotEmpty() && videoDigits == targetDigits) ||
            videoClass.contains(className, ignoreCase = true) ||
            className.contains(videoClass, ignoreCase = true)
        }
    }

    suspend fun getVideoById(videoId: String): Video? {
        val videos = getVideos()
        return videos.find { it.id == videoId }
    }

    suspend fun addVideo(video: Video) {
        val newVideo = video
        client.postgrest["videos"].insert(if (newVideo.id.isEmpty() || newVideo.id.length > 20) getJsonWithoutId(newVideo) else newVideo)
        notifyVideosChanged()
    }
    
    suspend fun updateVideo(video: Video) {
        if (video.id.isNotEmpty()) {
            client.postgrest["videos"].update(video) {
                filter { eq("id", video.id) }
            }
            notifyVideosChanged()
        }
    }
    
    suspend fun deleteVideo(videoId: String) {
        if (videoId.isNotEmpty()) {
            val idValue: Any = videoId.toLongOrNull() ?: videoId
            client.postgrest["videos"].delete {
                filter { eq("id", idValue) }
            }
            notifyVideosChanged()
        }
    }
    
    suspend fun getUsersCount(): Int {
        return try {
            // Very hacky but it works for now
            val users = client.postgrest["users"].select().decodeList<User>()
            users.size
        } catch (e: Exception) {
            0
        }
    }

    // Banners
    // QuestionPapers
    suspend fun getQuestionPapers(): List<QuestionPaper> {
        return try {
            client.postgrest["question_papers"].select().decodeList<QuestionPaper>()
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("AuraRepository", "Error fetching question papers: ${e.message}", e)
            emptyList()
        }
    }
    suspend fun getBanners(): List<Banner> {
        return try {
            client.postgrest["banners"].select {
                filter { eq("isEnabled", true) }
            }.decodeList<Banner>().sortedBy { it.order }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getAnnouncements(): List<com.example.data.models.Announcement> {
        return try {
            client.postgrest["announcements"].select {
                filter { eq("isEnabled", true) }
            }.decodeList<com.example.data.models.Announcement>().sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getHomeSectionConfigs(onlyVisible: Boolean = false): List<com.example.data.models.HomeSectionConfig> {
        val defaultSections = listOf(
            com.example.data.models.HomeSectionConfig("default_home", "home", "Home", "home", true, 1),
            com.example.data.models.HomeSectionConfig("default_books", "books", "Books", "book", true, 2),
            com.example.data.models.HomeSectionConfig("default_videos", "videos", "Videos", "play_circle", true, 3)
        )
        return try {
            val response = if (onlyVisible) {
                client.postgrest["home_sections"].select {
                    filter {
                        eq("isVisible", true)
                    }
                }.decodeList<com.example.data.models.HomeSectionConfig>()
            } else {
                client.postgrest["home_sections"].select().decodeList<com.example.data.models.HomeSectionConfig>()
            }
            // Filter out default sections if they exist in the DB response to prevent overrides or duplicates
            val filteredResponse = response.filter {
                it.type != "home" && it.type != "books" && it.type != "videos"
            }
            val combined = (defaultSections + filteredResponse).sortedBy { it.order }
            println("Fetched combined home sections: $combined")
            android.util.Log.d("AuraRepository", "Fetched combined home sections: $combined")
            combined
        } catch (e: Exception) {
            println("Error fetching home sections, returning hardcoded defaults: ${e.message}")
            android.util.Log.e("AuraRepository", "Error fetching dynamic sections, returning defaults", e)
            e.printStackTrace()
            defaultSections
        }
    }

    suspend fun addHomeSectionConfig(config: com.example.data.models.HomeSectionConfig) {
        try {
            client.postgrest["home_sections"].insert(if (config.id.isEmpty() || config.id.length > 20) getJsonWithoutId(config) else config)
            notifyHomeConfigChanged()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun addBanner(banner: Banner) {
        try {
            val newBanner = banner
            client.postgrest["banners"].insert(if (newBanner.id.isEmpty() || newBanner.id.length > 20) getJsonWithoutId(newBanner) else newBanner)
            notifyHomeConfigChanged()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun updateBanner(banner: Banner) {
        try {
            client.postgrest["banners"].update(banner) {
                filter { eq("id", banner.id) }
            }
            notifyHomeConfigChanged()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun deleteBanner(bannerId: String) {
        try {
            val idValue: Any = bannerId.toLongOrNull() ?: bannerId
            client.postgrest["banners"].delete {
                filter { eq("id", idValue) }
            }
            notifyHomeConfigChanged()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun updateHomeSectionConfig(config: com.example.data.models.HomeSectionConfig) {
        try {
            client.postgrest["home_sections"].update(config) {
                filter { eq("id", config.id) }
            }
            notifyHomeConfigChanged()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun addQuestionPaper(paper: QuestionPaper) {
        try {
            val newPaper = if (paper.id.isEmpty()) paper.copy(createdAt = System.currentTimeMillis()) else paper
            client.postgrest["question_papers"].insert(if (paper.id.isEmpty() || paper.id.length > 20) getJsonWithoutId(newPaper) else newPaper)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
    
    suspend fun updateQuestionPaper(paper: QuestionPaper) {
        try {
            if (paper.id.isNotEmpty()) {
                client.postgrest["question_papers"].update(paper) {
                    filter { eq("id", paper.id) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
    
    suspend fun deleteQuestionPaper(paperId: String) {
        try {
            if (paperId.isNotEmpty()) {
                val idValue: Any = paperId.toLongOrNull() ?: paperId
                client.postgrest["question_papers"].delete {
                    filter { eq("id", idValue) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    // QuestionPaperSections
    suspend fun getQuestionPaperSections(): List<QuestionPaperSection> {
        return try {
            client.postgrest["question_paper_sections"].select().decodeList<QuestionPaperSection>().sortedBy { it.order }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addQuestionPaperSection(section: QuestionPaperSection) {
        try {
            val newSection = if (section.id.isEmpty()) section.copy(createdAt = System.currentTimeMillis()) else section
            client.postgrest["question_paper_sections"].insert(if (section.id.isEmpty() || section.id.length > 20) getJsonWithoutId(newSection) else newSection)
            notifySectionsChanged()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun updateQuestionPaperSection(section: QuestionPaperSection) {
        try {
            if (section.id.isNotEmpty()) {
                client.postgrest["question_paper_sections"].update(section) {
                    filter { eq("id", section.id) }
                }
                notifySectionsChanged()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun deleteQuestionPaperSection(sectionId: String) {
        try {
            if (sectionId.isNotEmpty()) {
                val idValue: Any = sectionId.toLongOrNull() ?: sectionId
                client.postgrest["question_paper_sections"].delete {
                    filter { eq("id", idValue) }
                }
                notifySectionsChanged()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    // Websites
    suspend fun getWebsites(): List<com.example.data.models.Website> {
        return try {
            client.postgrest["websites"].select().decodeList<com.example.data.models.Website>()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    suspend fun addWebsite(website: com.example.data.models.Website) {
        try {
            val newWebsite = if (website.id.isEmpty()) website.copy(createdAt = System.currentTimeMillis()) else website
            client.postgrest["websites"].insert(if (website.id.isEmpty() || website.id.length > 20) getJsonWithoutId(newWebsite) else newWebsite)
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }
    
    suspend fun updateWebsite(website: com.example.data.models.Website) {
        try {
            if (website.id.isNotEmpty()) {
                client.postgrest["websites"].update(website) {
                    filter { eq("id", website.id) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun deleteWebsite(websiteId: String) {
        try {
            if (websiteId.isNotEmpty()) {
                val idValue: Any = websiteId.toLongOrNull() ?: websiteId
                client.postgrest["websites"].delete {
                    filter { eq("id", idValue) }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }


    // Notes
    suspend fun getNotesByUser(userId: String): List<Note> {
        return try {
            val notes = client.postgrest["notes"].select {
                filter { eq("userId", userId) }
            }.decodeList<Note>()
            notes.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addNote(note: Note) {
        try {
            val newNote = if (note.id.isEmpty()) note.copy(createdAt = System.currentTimeMillis()) else note
            client.postgrest["notes"].insert(if (note.id.isEmpty() || note.id.length > 20) getJsonWithoutId(newNote) else newNote)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteNote(noteId: String) {
        if (noteId.isNotEmpty()) {
            try {
                client.postgrest["notes"].delete {
                    filter { eq("id", noteId) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Flashcards
    suspend fun getFlashcardDecksByUser(userId: String): List<FlashcardDeck> {
        return try {
            val decks = client.postgrest["flashcard_decks"].select {
                filter { eq("userId", userId) }
            }.decodeList<FlashcardDeck>()
            decks.sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addFlashcardDeck(deck: FlashcardDeck): String {
        return try {
            
            val newDeck = if (deck.id.isEmpty()) deck.copy(createdAt = System.currentTimeMillis()) else deck
            val result = client.postgrest["flashcard_decks"].insert(if (deck.id.isEmpty() || deck.id.length > 20) getJsonWithoutId(newDeck) else newDeck) { select() }
            val inserted = result.decodeSingle<FlashcardDeck>()
            inserted.id
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    suspend fun deleteFlashcardDeck(deckId: String) {
        if (deckId.isNotEmpty()) {
            try {
                client.postgrest["flashcard_decks"].delete {
                    filter { eq("id", deckId) }
                }
                client.postgrest["flashcards"].delete {
                    filter { eq("deckId", deckId) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun getFlashcardsByDeck(deckId: String): List<Flashcard> {
        return try {
            val cards = client.postgrest["flashcards"].select {
                filter { eq("deckId", deckId) }
            }.decodeList<Flashcard>()
            cards.sortedBy { it.createdAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addFlashcard(card: Flashcard) {
        try {
            val newCard = if (card.id.isEmpty()) card.copy(createdAt = System.currentTimeMillis()) else card
            client.postgrest["flashcards"].insert(if (card.id.isEmpty() || card.id.length > 20) getJsonWithoutId(newCard) else newCard)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteFlashcard(cardId: String) {
        if (cardId.isNotEmpty()) {
            try {
                client.postgrest["flashcards"].delete {
                    filter { eq("id", cardId) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Progress Tracking
    suspend fun getVideoProgress(userId: String): List<VideoProgress> {
        return try {
            client.postgrest["video_progress"].select {
                filter { eq("userId", userId) }
            }.decodeList<VideoProgress>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateVideoProgress(progress: VideoProgress) {
        try {
            val existing = client.postgrest["video_progress"].select {
                filter {
                    eq("userId", progress.userId)
                    eq("videoId", progress.videoId)
                }
            }.decodeList<VideoProgress>().firstOrNull()

            if (existing != null) {
                if (!existing.isWatched && progress.isWatched) {
                    awardPoints(progress.userId, 50, "Completed Video Lesson")
                }
                client.postgrest["video_progress"].update(progress.copy(id = existing.id, lastWatchedAt = System.currentTimeMillis())) {
                    filter { eq("id", existing.id) }
                }
            } else {
                if (progress.isWatched) {
                    awardPoints(progress.userId, 50, "Completed Video Lesson")
                }
                client.postgrest["video_progress"].insert(getJsonWithoutId(progress.copy(lastWatchedAt = System.currentTimeMillis())))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun getBookProgress(userId: String): List<BookProgress> {
        return try {
            client.postgrest["book_progress"].select {
                filter { eq("userId", userId) }
            }.decodeList<BookProgress>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun updateBookProgress(progress: BookProgress) {
        try {
            val existing = client.postgrest["book_progress"].select {
                filter {
                    eq("userId", progress.userId)
                    eq("bookId", progress.bookId)
                }
            }.decodeList<BookProgress>().firstOrNull()

            if (existing != null) {
                // Award points if they read significantly more
                if (progress.lastPage > existing.lastPage + 5) {
                    awardPoints(progress.userId, 30, "Reading Milestone")
                }
                client.postgrest["book_progress"].update(progress.copy(id = existing.id, lastReadAt = System.currentTimeMillis())) {
                    filter { eq("id", existing.id) }
                }
            } else {
                if (progress.lastPage > 0) {
                    awardPoints(progress.userId, 10, "Started Reading")
                }
                client.postgrest["book_progress"].insert(getJsonWithoutId(progress.copy(lastReadAt = System.currentTimeMillis())))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Exam Boards (Exam Results websites)
    // Quizzes
    suspend fun getQuizzes(className: String = "", subject: String = "", associatedId: String = ""): List<com.example.data.models.Quiz> {
        return try {
            val query = client.postgrest["quizzes"].select {
                if (className.isNotEmpty()) filter { eq("className", className) }
                if (subject.isNotEmpty()) filter { eq("subject", subject) }
                if (associatedId.isNotEmpty()) filter { eq("associatedId", associatedId) }
            }
            query.decodeList<com.example.data.models.Quiz>().sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun addQuiz(quiz: com.example.data.models.Quiz): String {
        return try {
            val newQuiz = if (quiz.id.isEmpty()) quiz.copy(createdAt = System.currentTimeMillis()) else quiz
            val result = client.postgrest["quizzes"].insert(if (quiz.id.isEmpty() || quiz.id.length > 20) getJsonWithoutId(newQuiz) else newQuiz) { select() }
            val inserted = result.decodeSingle<com.example.data.models.Quiz>()
            inserted.id
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    suspend fun deleteQuiz(quizId: String) {
        if (quizId.isNotEmpty()) {
            try {
                client.postgrest["quizzes"].delete { filter { eq("id", quizId) } }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    suspend fun getQuizQuestions(quizId: String): List<com.example.data.models.QuizQuestion> {
        return try {
            client.postgrest["quiz_questions"].select {
                filter { eq("quizId", quizId) }
            }.decodeList<com.example.data.models.QuizQuestion>().sortedBy { it.order }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun saveQuizQuestions(quizId: String, questions: List<com.example.data.models.QuizQuestion>) {
        try {
            client.postgrest["quiz_questions"].delete { filter { eq("quizId", quizId) } }
            if (questions.isNotEmpty()) {
                val newQuestions = questions.map { it.copy(quizId = quizId) }
                client.postgrest["quiz_questions"].insert(getJsonListWithoutId(newQuestions))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun saveQuizResult(result: com.example.data.models.QuizResult) {
        try {
            val newResult = if(result.id.isEmpty()) result.copy(createdAt = System.currentTimeMillis()) else result
            client.postgrest["quiz_results"].insert(if (result.id.isEmpty() || result.id.length > 20) getJsonWithoutId(newResult) else newResult)
            
            // Award points for quiz
            val pointsToAward = (result.score * 10) + (if (result.score == result.totalQuestions && result.totalQuestions > 0) 20 else 0)
            if (pointsToAward > 0) {
                awardPoints(result.userId, pointsToAward, "Quiz: ${result.score}/${result.totalQuestions}")
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Gamification Logic
    suspend fun awardPoints(userId: String, points: Int, reason: String) {
        try {
            val user = getUserProfile(userId) ?: return
            val newPoints = user.points + points
            val newLevel = (newPoints / 500) + 1
            
            val updatedUser = user.copy(
                points = newPoints,
                level = newLevel,
                rank = when {
                    newPoints > 5000 -> "Diamond Elite"
                    newPoints > 2500 -> "Platinum Scholar"
                    newPoints > 1000 -> "Gold Learner"
                    newPoints > 500 -> "Silver Achiever"
                    else -> "Bronze Starter"
                }
            )
            
            updateUserProfile(updatedUser)
            checkAndAwardBadges(updatedUser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private suspend fun checkAndAwardBadges(user: User) {
        val currentBadges = user.badges.toMutableList()
        var changed = false

        // 1. Fast Learner
        if (!currentBadges.contains("Fast Learner")) {
            val progress = getVideoProgress(user.id)
            if (progress.any { it.isWatched }) {
                currentBadges.add("Fast Learner")
                changed = true
            }
        }

        // 2. Quiz Master
        if (!currentBadges.contains("Quiz Master")) {
            val results = getQuizResultsByUser(user.id)
            if (results.any { it.score == it.totalQuestions && it.totalQuestions > 0 }) {
                currentBadges.add("Quiz Master")
                changed = true
            }
        }

        // 3. Points Milestone
        if (user.points >= 1000 && !currentBadges.contains("Points Millionaire")) {
            currentBadges.add("Points Millionaire")
            changed = true
        }

        if (changed) {
            updateUserProfile(user.copy(badges = currentBadges))
        }
    }

    suspend fun getLeaderboard(): List<com.example.data.models.LeaderboardEntry> {
        return try {
            val users = client.postgrest["users"].select {
                order("points", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                limit(50)
            }.decodeList<User>()
            
            users.mapIndexed { index, user ->
                com.example.data.models.LeaderboardEntry(
                    userId = user.id,
                    name = user.name,
                    photoUrl = user.photoUrl,
                    points = user.points,
                    rank = index + 1,
                    level = user.level
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getQuizResultsByUser(userId: String): List<com.example.data.models.QuizResult> {
        return try {
            client.postgrest["quiz_results"].select {
                filter { eq("userId", userId) }
            }.decodeList<com.example.data.models.QuizResult>().sortedByDescending { it.createdAt }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun getExamBoards(): List<com.example.ui.profile.BoardResult> {
        return try {
            client.postgrest["exam_boards"].select().decodeList<com.example.ui.profile.BoardResult>()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun addExamBoard(board: com.example.ui.profile.BoardResult) {
        try {
            val newBoard = if (board.id.isEmpty()) board.copy(createdAt = System.currentTimeMillis()) else board
            client.postgrest["exam_boards"].insert(if (board.id.isEmpty() || board.id.length > 20) getJsonWithoutId(newBoard) else newBoard)
            notifyBoardsChanged()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun updateExamBoard(board: com.example.ui.profile.BoardResult) {
        try {
            if (board.id.isNotEmpty()) {
                client.postgrest["exam_boards"].update(board) {
                    filter { eq("id", board.id) }
                }
                notifyBoardsChanged()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun deleteExamBoard(boardId: String) {
        try {
            if (boardId.isNotEmpty()) {
                client.postgrest["exam_boards"].delete {
                    filter { eq("id", boardId) }
                }
                notifyBoardsChanged()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    // Notifications
    suspend fun addNotification(notification: com.example.data.repository.notifications.SupabaseNotification) {
        try {
            client.postgrest["notifications"].insert(if (notification.id.isEmpty() || notification.id.length > 20) getJsonWithoutId(notification) else notification)
            notifyNotificationsChanged()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        }
    }

    suspend fun uploadImage(imageBytes: ByteArray, fileName: String, bucketName: String = "uploads"): String {
        return try {
            val bucket = client.storage[bucketName]
            bucket.upload(fileName, imageBytes) { upsert = true }
            bucket.publicUrl(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    suspend fun uploadResultImage(imageBytes: ByteArray, fileName: String): String {
        return try {
            val bucket = client.storage["results"]
            bucket.upload(fileName, imageBytes) { upsert = true }
            bucket.publicUrl(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    suspend fun uploadCoverImage(imageBytes: ByteArray, fileName: String): String {
        return try {
            val bucket = client.storage["covers"]
            bucket.upload(fileName, imageBytes) { upsert = true }
            bucket.publicUrl(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    suspend fun uploadBookPdf(pdfBytes: ByteArray, fileName: String): String {
        return try {
            val bucket = client.storage["books"]
            bucket.upload(fileName, pdfBytes) { upsert = true }
            bucket.publicUrl(fileName)
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback to "covers" bucket
            try {
                val bucket = client.storage["covers"]
                bucket.upload(fileName, pdfBytes) { upsert = true }
                bucket.publicUrl(fileName)
            } catch (ex: Exception) {
                ex.printStackTrace()
                ""
            }
        }
    }

    fun subscribeToQuestionPapers(onChanged: () -> Unit) {
        val scope = CoroutineScope(Dispatchers.IO)
        scope.launch {
            try {
                val channel = client.realtime.channel("question-papers-updates")
                val changes = channel.postgresChangeFlow<PostgresAction>("public") {
                    table = "question_papers"
                }
                scope.launch {
                    changes.collect {
                        onChanged()
                    }
                }
                channel.subscribe()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    // Feedback Methods
    suspend fun submitFeedback(feedback: com.example.data.models.Feedback): Boolean {
        return try {
            client.postgrest["feedback"].insert(if (feedback.id.isEmpty() || feedback.id.length > 20) getJsonWithoutId(feedback) else feedback)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun getAllFeedback(): List<com.example.data.models.Feedback> {
        return try {
            client.postgrest["feedback"].select {
                order("createdAt", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
            }.decodeList<com.example.data.models.Feedback>()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun updateFeedbackStatus(feedbackId: String, status: String): Boolean {
        return try {
            client.postgrest["feedback"].update({
                set("status", status)
                set("updatedAt", System.currentTimeMillis())
            }) {
                filter {
                    eq("id", feedbackId)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun deleteFeedback(feedbackId: String): Boolean {
        return try {
            client.postgrest["feedback"].delete {
                filter {
                    eq("id", feedbackId)
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun toggleUpvoteFeedback(feedbackId: String, userId: String): Boolean {
        return try {
            // First fetch the current feedback
            val feedbacks = client.postgrest["feedback"].select {
                filter { eq("id", feedbackId) }
            }.decodeList<com.example.data.models.Feedback>()
            
            val feedback = feedbacks.firstOrNull()
            if (feedback != null) {
                val upvotedByList = feedback.upvotedBy.toMutableList()
                val isUpvoted = upvotedByList.contains(userId)
                
                if (isUpvoted) {
                    upvotedByList.remove(userId)
                } else {
                    upvotedByList.add(userId)
                }
                
                client.postgrest["feedback"].update({
                    set("upvotes", upvotedByList.size)
                    set("upvotedBy", upvotedByList)
                }) {
                    filter { eq("id", feedbackId) }
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    suspend fun uploadFeedbackScreenshot(imageBytes: ByteArray, fileName: String): String {
        return try {
            val bucket = client.storage["feedback_screenshots"]
            bucket.upload(fileName, imageBytes) { upsert = true }
            bucket.publicUrl(fileName)
        } catch (e: Exception) {
            // Fallback to covers bucket if specialized bucket doesn't exist
            try {
                val bucket = client.storage["covers"]
                bucket.upload("feedback/$fileName", imageBytes) { upsert = true }
                bucket.publicUrl("feedback/$fileName")
            } catch (ex: Exception) {
                ex.printStackTrace()
                ""
            }
        }
    }
}
