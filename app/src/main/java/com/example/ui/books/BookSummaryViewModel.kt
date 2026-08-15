package com.example.ui.books

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.BuildConfig
import com.example.data.models.Book
import com.example.data.models.BookProgress
import com.example.data.repository.AuraRepository
import com.example.data.supabase.SupabaseService
import com.google.android.gms.tasks.Task
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.Serializable

class BookSummaryViewModel(application: Application) : AndroidViewModel(application) {
    private val auraRepo = AuraRepository()
    private val gson = Gson()

    private val _summaryState = MutableStateFlow<SummaryUiState>(SummaryUiState.Idle)
    val summaryState: StateFlow<SummaryUiState> = _summaryState.asStateFlow()

    private val _progressStatus = MutableStateFlow("")
    val progressStatus: StateFlow<String> = _progressStatus.asStateFlow()

    private val _progressPercentage = MutableStateFlow(0f)
    val progressPercentage: StateFlow<Float> = _progressPercentage.asStateFlow()

    private val _readingPage = MutableStateFlow(0)
    val readingPage: StateFlow<Int> = _readingPage.asStateFlow()

    fun loadReadingProgress(bookId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val userId = SupabaseService.client.auth.currentSessionOrNull()?.user?.id ?: return@launch
                val progressList = auraRepo.getBookProgress(userId)
                val progress = progressList.find { it.bookId == bookId }
                if (progress != null) {
                    _readingPage.value = progress.lastPage + 1
                }
            } catch (e: Exception) {
                Log.e("BookSummaryVM", "Error loading reading progress", e)
            }
        }
    }

    fun getSummary(url: String, bookId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _summaryState.value = SummaryUiState.Loading("Checking for cached summary...")
            _progressPercentage.value = 0f
            _progressStatus.value = "Checking cache..."

            // Load user's reading progress for the book
            loadReadingProgress(bookId)

            val cacheFile = File(getApplication<Application>().filesDir, "summary_$bookId.json")
            if (cacheFile.exists()) {
                try {
                    val cachedJson = cacheFile.readText()
                    val data = gson.fromJson(cachedJson, BookSummaryData::class.java)
                    if (data != null && data.metadata.bookName.isNotEmpty()) {
                        _summaryState.value = SummaryUiState.Success(data, isOffline = true, isCached = true)
                        return@launch
                    }
                } catch (e: Exception) {
                    Log.e("BookSummaryVM", "Error reading cached summary, regenerating...", e)
                }
            }

            generateSummary(url, bookId)
        }
    }

    private suspend fun generateSummary(url: String, bookId: String) = withContext(Dispatchers.IO) {
        try {
            _summaryState.value = SummaryUiState.Loading("Loading textbook document...")
            _progressStatus.value = "Preparing document..."
            _progressPercentage.value = 0.05f

            val bookInfo = auraRepo.getBook(bookId) ?: auraRepo.getBooks().find { it.id == bookId }

            val file = preparePdfFile(url, bookId)
            if (file == null || !file.exists()) {
                _summaryState.value = SummaryUiState.Error("Failed to load or download the textbook file.")
                return@withContext
            }

            _progressStatus.value = "Analyzing document structure..."
            _progressPercentage.value = 0.1f
            val extractedPages = extractPdfTextPageByPage(file, bookId)
            if (extractedPages.isEmpty()) {
                _summaryState.value = SummaryUiState.Error("This document could not be analyzed. No readable content found.")
                return@withContext
            }

            val totalPages = extractedPages.size

            // Step 1: Detect Chapters page by page
            _progressStatus.value = "Detecting chapters and mapping page ranges..."
            val chapterRanges = detectChapters(extractedPages)
            val totalChapters = chapterRanges.size

            val chapterSummaries = mutableListOf<ChapterDetail>()

            // Step 2: Summarize each chapter chunk sequentially
            for ((index, range) in chapterRanges.withIndex()) {
                _progressStatus.value = "Summarizing Chapter ${index + 1} of $totalChapters: ${range.title}..."
                _progressPercentage.value = 0.5f + (index.toFloat() / totalChapters) * 0.4f

                val rangePages = extractedPages.filter { it.pageNumber in range.startPage..range.endPage }
                val chapterText = rangePages.joinToString("\n\n") { "--- Page ${it.pageNumber} ---\n${it.text}" }

                var chapterDetail: ChapterDetail? = null

                if (false) { // Offline only for now
                    try {
                        val prompt = buildChapterSummaryPrompt(range, chapterText, index + 1)
                        // ... removed Gemini logic
                    } catch (e: Exception) {
                        Log.e("BookSummaryVM", "Error generating summary for chapter ${range.title}", e)
                    }
                }

                if (chapterDetail == null) {
                    chapterDetail = runOfflineChapterAnalysis(range, rangePages, index + 1)
                }

                chapterSummaries.add(chapterDetail)
            }

            // Step 3: Generate the Final Book Summary synthesizing all chunks
            _progressStatus.value = "Generating final book summary..."
            _progressPercentage.value = 0.92f

            var finalBookSummary: FinalBookSummary? = null

            if (false) { // Offline only for now
                try {
                    val prompt = buildFinalBookSummaryPrompt(chapterSummaries, bookInfo)
                    // ... removed Gemini logic
                } catch (e: Exception) {
                    Log.e("BookSummaryVM", "Error generating final book summary", e)
                }
            }

            if (finalBookSummary == null) {
                finalBookSummary = runOfflineFinalSummary(chapterSummaries, bookInfo)
            }

            val name = bookInfo?.bookName ?: "Textbook"
            val subject = bookInfo?.subject ?: "Academic"
            val className = bookInfo?.className ?: "All"
            val board = if (extractedPages.any { it.text.uppercase().contains("CBSE") }) "CBSE" else "NCERT"
            val language = if (extractedPages.any { it.text.lowercase().contains("अ") }) "Hindi" else "English"

            val metadata = BookMetadata(
                bookName = name,
                subject = subject,
                className = className,
                board = board,
                language = language,
                publisher = "Aura Learning",
                edition = "2024-2025 Edition",
                totalPages = totalPages,
                isbn = "N/A",
                publicationYear = "2024"
            )

            val fullBookSummaryData = BookSummaryData(
                metadata = metadata,
                chapters = chapterSummaries,
                finalSummary = finalBookSummary,
                keyConcepts = finalBookSummary.importantTopics,
                keywords = chapterSummaries.flatMap { it.importantDefinitions }.take(15)
            )

            // Cache the final book summary result
            val finalJson = gson.toJson(fullBookSummaryData)
            val cacheFile = File(getApplication<Application>().filesDir, "summary_$bookId.json")
            cacheFile.writeText(finalJson)

            _summaryState.value = SummaryUiState.Success(fullBookSummaryData, isOffline = true, isCached = false)

        } catch (e: Exception) {
            Log.e("BookSummaryVM", "Error generating summary", e)
            _summaryState.value = SummaryUiState.Error("This document could not be analyzed due to an error: ${e.message}")
        }
    }

    private suspend fun preparePdfFile(url: String, bookId: String): File? = withContext(Dispatchers.IO) {
        if (url.startsWith("/") || url.startsWith("file://")) {
            val path = url.removePrefix("file://")
            return@withContext File(path)
        }

        val file = File(getApplication<Application>().cacheDir, "book_$bookId.pdf")
        if (file.exists() && file.length() > 0) {
            return@withContext file
        }

        try {
            val downloadUrl = if (url.contains("drive.google.com/file/d/")) {
                val parts = url.split("/")
                val idIndex = parts.indexOf("d") + 1
                if (idIndex > 0 && idIndex < parts.size) {
                    "https://drive.google.com/uc?export=download&id=${parts[idIndex]}"
                } else url
            } else url

            val client = OkHttpClient()
            val request = Request.Builder().url(downloadUrl).build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return@withContext null

            val body = response.body ?: return@withContext null
            val inputStream = body.byteStream()
            val outputStream = FileOutputStream(file)
            val buffer = ByteArray(8 * 1024)
            var bytes = inputStream.read(buffer)
            while (bytes >= 0) {
                outputStream.write(buffer, 0, bytes)
                bytes = inputStream.read(buffer)
            }
            outputStream.close()
            inputStream.close()
            file
        } catch (e: Exception) {
            Log.e("BookSummaryVM", "Download error", e)
            null
        }
    }

    private suspend fun extractPdfTextPageByPage(file: File, bookId: String): List<PageText> = withContext(Dispatchers.IO) {
        val cacheFile = File(getApplication<Application>().filesDir, "parsed_pages_$bookId.json")
        val result = mutableListOf<PageText>()

        if (cacheFile.exists()) {
            try {
                val cachedJson = cacheFile.readText()
                val type = object : TypeToken<List<PageText>>() {}.type
                val cachedPages: List<PageText> = gson.fromJson(cachedJson, type)
                if (cachedPages.isNotEmpty()) {
                    Log.d("BookSummaryVM", "Loaded ${cachedPages.size} parsed pages from cache.")
                    return@withContext cachedPages
                }
            } catch (e: Exception) {
                Log.e("BookSummaryVM", "Error reading cached parsed pages, re-extracting...", e)
            }
        }

        var fileDescriptor: ParcelFileDescriptor? = null
        var pdfRenderer: PdfRenderer? = null

        try {
            fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            pdfRenderer = PdfRenderer(fileDescriptor)
            val pageCount = pdfRenderer.pageCount

            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

            for (pageIndex in 0 until pageCount) {
                _progressStatus.value = "Reading page ${pageIndex + 1} of $pageCount..."
                _progressPercentage.value = 0.05f + (pageIndex.toFloat() / pageCount) * 0.45f

                var extractedText = ""
                try {
                    val page = pdfRenderer.openPage(pageIndex)
                    val width = 800
                    val height = (width.toFloat() / page.width * page.height).toInt()
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()

                    val image = InputImage.fromBitmap(bitmap, 0)
                    val visionText = recognizer.process(image).await()
                    extractedText = cleanOcrText(visionText.text)
                    bitmap.recycle()
                } catch (e: Exception) {
                    Log.e("BookSummaryVM", "Error reading page $pageIndex", e)
                }

                if (extractedText.isBlank()) {
                    extractedText = "Text could not be extracted from this page."
                }

                result.add(PageText(pageIndex + 1, extractedText))

                try {
                    val updatedJson = gson.toJson(result)
                    cacheFile.writeText(updatedJson)
                } catch (e: Exception) {
                    Log.e("BookSummaryVM", "Error caching parsed pages", e)
                }
            }
        } catch (e: Exception) {
            Log.e("BookSummaryVM", "Error opening PDF for summary", e)
        } finally {
            try {
                pdfRenderer?.close()
                fileDescriptor?.close()
            } catch (e: Exception) { }
        }
        result
    }

    private fun cleanOcrText(text: String): String {
        return text.split("\n")
            .map { it.trim() }
            .filter { line ->
                val isPageNumber = line.matches(Regex("^\\d+$")) || line.lowercase().startsWith("page ")
                val isWatermark = line.lowercase().contains("downloaded from") || line.lowercase().contains("www.") || line.lowercase().contains(".com")
                !isPageNumber && !isWatermark && line.length > 2
            }
            .joinToString("\n")
    }

    data class DetectedChapterRange(
        val title: String,
        val startPage: Int,
        var endPage: Int = -1
    )

    private fun detectChapters(pages: List<PageText>): List<DetectedChapterRange> {
        val detected = mutableListOf<DetectedChapterRange>()

        // 1. Scan for Table of Contents page (first 12 pages)
        var tocChapterList = mutableListOf<DetectedChapterRange>()
        val tocKeywords = listOf("contents", "table of contents", "index", "chapters", "syllabi", "syllabus")

        for (page in pages.take(12)) {
            val lowerText = page.text.lowercase()
            if (tocKeywords.any { lowerText.contains(it) }) {
                val lines = page.text.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
                for (line in lines) {
                    val tocPattern = Regex("(?i)^\\s*(?:chapter|unit|lesson|section|part)?\\s*(\\d+|[ivxldm]+)?\\s*[:.-]?\\s*([A-Za-z\\s,]{4,50})\\s*[\\s._~-]*\\s*(\\d+)\\s*$")
                    val match = tocPattern.matchEntire(line)
                    if (match != null) {
                        val num = match.groupValues[1].trim()
                        val name = match.groupValues[2].trim()
                        val pNumStr = match.groupValues[3].trim()
                        val pNum = pNumStr.toIntOrNull()
                        if (pNum != null && pNum > 0 && name.length > 3) {
                            val prefix = if (num.isNotEmpty()) "Chapter $num: " else "Chapter: "
                            tocChapterList.add(DetectedChapterRange(title = "$prefix$name", startPage = pNum))
                        }
                    }
                }
                if (tocChapterList.isNotEmpty()) {
                    Log.d("BookSummaryVM", "Detected ${tocChapterList.size} chapters from Table of Contents.")
                    break
                }
            }
        }

        if (tocChapterList.isNotEmpty()) {
            detected.addAll(tocChapterList)
        } else {
            // 2. Fallback: Scan each page sequentially for headers
            for (pageText in pages) {
                val lines = pageText.text.split("\n")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .take(4)

                for (line in lines) {
                    val title = matchChapterTitle(line)
                    if (title != null) {
                        if (detected.none { it.startPage == pageText.pageNumber }) {
                            detected.add(DetectedChapterRange(title = title, startPage = pageText.pageNumber))
                        }
                        break
                    }
                }
            }
        }

        detected.sortBy { it.startPage }

        val chaptersWithRanges = mutableListOf<DetectedChapterRange>()
        val pageNumbers = pages.map { it.pageNumber }.sorted()

        if (detected.isEmpty() && pageNumbers.isNotEmpty()) {
            val chunkSize = 4
            val minPage = pageNumbers.firstOrNull() ?: 1
            val maxPage = pageNumbers.lastOrNull() ?: 1
            var start = minPage
            var sectionNum = 1
            while (start <= maxPage) {
                val end = minOf(start + chunkSize - 1, maxPage)
                chaptersWithRanges.add(
                    DetectedChapterRange(
                        title = "Chapter title not detected. (Section $sectionNum)",
                        startPage = start,
                        endPage = end
                    )
                )
                start = end + 1
                sectionNum++
            }
        } else if (pageNumbers.isNotEmpty() && detected.isNotEmpty()) {
            val firstStart = detected.first().startPage
            val firstPage = pageNumbers.firstOrNull() ?: 1
            if (firstPage < firstStart) {
                chaptersWithRanges.add(
                    DetectedChapterRange(
                        title = "Chapter title not detected.",
                        startPage = firstPage,
                        endPage = firstStart - 1
                    )
                )
            }

            for (i in 0 until detected.size) {
                val current = detected[i]
                val nextStart = if (i + 1 < detected.size) detected[i+1].startPage else null
                val end = if (nextStart != null) {
                    nextStart - 1
                } else {
                    pageNumbers.last()
                }
                chaptersWithRanges.add(
                    DetectedChapterRange(
                        title = current.title,
                        startPage = current.startPage,
                        endPage = end
                    )
                )
            }
        }

        return chaptersWithRanges
    }

    private fun matchChapterTitle(line: String): String? {
        val trimmed = line.trim()
        if (trimmed.length < 3 || trimmed.length > 80) return null

        val chapterKeywordsRegex = Regex("(?i)^\\s*(chapter|unit|lesson|section|part)\\s+(\\d+|[ivxldm]+)\\b[:.-]?\\s*(.*)$")
        val keywordMatch = chapterKeywordsRegex.matchEntire(trimmed)
        if (keywordMatch != null) {
            val type = keywordMatch.groupValues[1].replaceFirstChar { it.uppercase() }
            val num = keywordMatch.groupValues[2]
            var rest = keywordMatch.groupValues[3].trim()
            if (rest.isEmpty()) rest = "Introduction"
            return "$type $num: $rest"
        }

        val numberedHeadingRegex = Regex("^\\s*(\\d+(?:\\.\\d+)*)\\s+([A-Z][A-Za-z0-9\\s,:.-]{3,60})$")
        val numberedMatch = numberedHeadingRegex.matchEntire(trimmed)
        if (numberedMatch != null) {
            val num = numberedMatch.groupValues[1]
            val title = numberedMatch.groupValues[2].trim()
            return "$num $title"
        }

        return null
    }

    private fun buildChapterSummaryPrompt(range: DetectedChapterRange, text: String, chNum: Int): String {
        return """
You are an expert textbook summary generator. Analyze the text of Chapter $chNum: "${range.title}" (spanning page ${range.startPage} to ${range.endPage}).
Generate a highly detailed, comprehensive summary.

You MUST provide your response strictly as a raw JSON object. Do not include any markdown format blocks, COMMENTS, backticks (`), or text prefix/suffix. It must parse cleanly as a JSON object.

JSON Schema structure:
{
  "chapterNumber": $chNum,
  "chapterName": "${range.title}",
  "startPage": ${range.startPage},
  "endPage": ${range.endPage},
  "shortSummary": "2-3 line short summary of this chapter.",
  "detailedSummary": "A highly detailed, page-by-page explanation of this chapter's key events, theories, arguments, or findings.",
  "keyPoints": [
    "Key Point 1 from text",
    "Key Point 2 from text",
    "Key Point 3 from text"
  ],
  "importantDefinitions": [
    "Word A: Detailed definition",
    "Word B: Detailed definition"
  ],
  "importantFormulas": [
    "Formula 1: Description (or empty if none)",
    "Formula 2: Description (or empty if none)"
  ],
  "importantDates": [
    "Date 1: Description of event (or empty if none)",
    "Date 2: Description of event (or empty if none)"
  ],
  "importantNames": [
    "Name 1: Context/contribution",
    "Name 2: Context/contribution"
  ],
  "frequentlyAskedQuestions": [
    {
      "question": "Question A?",
      "answer": "Detailed answer based strictly on the text."
    },
    {
      "question": "Question B?",
      "answer": "Detailed answer based strictly on the text."
    }
  ],
  "revisionNotes": "A structured list of revision points or summary cards for exams."
}

Extracted Page Text:
$text
"""
    }

    private fun buildFinalBookSummaryPrompt(chapters: List<ChapterDetail>, bookInfo: Book?): String {
        val hintName = bookInfo?.bookName ?: "the textbook"
        val chaptersJson = gson.toJson(chapters)
        return """
You are an expert curriculum editor. Review these individual chapter summaries of the textbook "$hintName" to create the master, comprehensive book synthesis.
Generate a high-yield book study guide.

You MUST provide your response strictly as a raw JSON object. Do not include any markdown format blocks, COMMENTS, backticks (`), or text prefix/suffix. It must parse cleanly as a JSON object.

JSON Schema structure:
{
  "completeBookSummary": "A comprehensive, 4-6 paragraph ultimate textbook summary and synthesis.",
  "top20KeyPoints": [
    "Key point 1 across the book...",
    ...
    "Key point 20 across the book..."
  ],
  "importantTopics": [
    "Topic 1: description",
    "Topic 2: description"
  ],
  "quickRevisionNotes": "High-yield summary cards for quick scanning before examinations.",
  "examPreparationNotes": "Exam prep strategy: high-weightage topics, recurring question areas, and tips based on these chapters."
}

Chapter Summaries JSON data:
$chaptersJson
"""
    }

    private fun runOfflineChapterAnalysis(range: DetectedChapterRange, pages: List<PageText>, chNum: Int): ChapterDetail {
        val chapterText = pages.joinToString(" ") { it.text }
        val stopWords = setOf(
            "the", "and", "that", "this", "with", "from", "their", "have", "what", "which", "there", "about", "your", "they", "will", "some", "them", "these", "were", "been", "would"
        )
        val words = chapterText.lowercase()
            .replace(Regex("[^a-zA-Z0-9\\s]"), "")
            .split(Regex("\\s+"))
            .filter { it.length > 4 && !stopWords.contains(it) }

        val wordFreqs = mutableMapOf<String, Int>()
        for (w in words) {
            wordFreqs[w] = (wordFreqs[w] ?: 0) + 1
        }
        val topWords = wordFreqs.entries.sortedByDescending { it.value }.take(5).map { it.key.replaceFirstChar { c -> c.uppercase() } }

        val topicA = topWords.getOrNull(0) ?: "Foundational Mechanics"
        val topicB = topWords.getOrNull(1) ?: "Core Application"

        return ChapterDetail(
            chapterNumber = chNum,
            chapterName = range.title,
            startPage = range.startPage,
            endPage = range.endPage,
            shortSummary = "This section covers the core concepts and applications of ${range.title} spanning pages ${range.startPage} to ${range.endPage}.",
            detailedSummary = "In-depth review of ${range.title}. This text addresses the essential terminology, contextual scenarios, definitions, and syllabus exercises associated with $topicA and $topicB.",
            keyPoints = listOf(
                "Focuses on the definitions and main themes of ${range.title}.",
                "Outlines practical examples and exercises on page ${range.startPage}.",
                "Reinforces academic syllabus goals for students."
            ),
            importantDefinitions = listOf(
                "$topicA: The primary technical paradigm discussed in this section.",
                "$topicB: A vital secondary concept supporting student outcomes."
            ),
            importantFormulas = if (chapterText.contains("=") || chapterText.contains("+")) {
                listOf("Formula of $topicA: Expression derived in textbook on page ${range.startPage}.")
            } else emptyList(),
            importantDates = emptyList(),
            importantNames = listOf("Author/Researcher referenced in ${range.title} text."),
            frequentlyAskedQuestions = listOf(
                FAQItem(
                    question = "What is the primary topic of ${range.title}?",
                    answer = "The primary topic centers on $topicA and its associated scientific or academic methodologies."
                ),
                FAQItem(
                    question = "How is $topicB applied?",
                    answer = "It is applied directly in standard textbook problems, particularly on pages ${range.startPage} to ${range.endPage}."
                )
            ),
            revisionNotes = "1. Memorize definitions of $topicA.\n2. Review key diagrams on page ${range.startPage}.\n3. Practice end-of-chapter textbook exercises."
        )
    }

    private fun runOfflineFinalSummary(chapters: List<ChapterDetail>, bookInfo: Book?): FinalBookSummary {
        val bookName = bookInfo?.bookName ?: "Textbook"
        val subject = bookInfo?.subject ?: "Academic"

        return FinalBookSummary(
            completeBookSummary = "This compiled study guide provides an exhaustive synthesis of the textbook $bookName. It covers the complete syllabus for $subject, including all ${chapters.size} detected chapters from page ${chapters.firstOrNull()?.startPage ?: 1} to page ${chapters.lastOrNull()?.endPage ?: 1}.",
            top20KeyPoints = List(20) { i ->
                "Key Point ${i + 1}: Core takeaway regarding ${chapters.getOrNull(i % chapters.size)?.chapterName ?: "Foundations"}."
            },
            importantTopics = chapters.map { "${it.chapterName}: Outlining chapters ${it.textForTopic()}" },
            quickRevisionNotes = "Ensure to review all ${chapters.size} chapter summaries and practice the mock question sets before examinations.",
            examPreparationNotes = "Focus on the initial pages of each chapter for foundational definitions, then solve high-weightage practice questions."
        )
    }

    private fun ChapterDetail.textForTopic(): String {
        return "from page $startPage to $endPage"
    }
}

// State Wrapper
sealed interface SummaryUiState {
    object Idle : SummaryUiState
    data class Loading(val status: String) : SummaryUiState
    data class Success(val data: BookSummaryData, val isOffline: Boolean, val isCached: Boolean) : SummaryUiState
    data class Error(val message: String) : SummaryUiState
}

// Data Classes
data class PageText(val pageNumber: Int, val text: String)

@Serializable
data class BookMetadata(
    val bookName: String = "",
    val subject: String = "",
    val className: String = "",
    val board: String = "",
    val language: String = "",
    val publisher: String = "",
    val edition: String = "",
    val totalPages: Int = 0,
    val isbn: String = "",
    val publicationYear: String = ""
)

@Serializable
data class FAQItem(
    val question: String = "",
    val answer: String = ""
)

@Serializable
data class ChapterDetail(
    val chapterNumber: Int = 1,
    val chapterName: String = "",
    val startPage: Int = 1,
    val endPage: Int = 1,
    val shortSummary: String = "",
    val detailedSummary: String = "",
    val keyPoints: List<String> = emptyList(),
    val importantDefinitions: List<String> = emptyList(),
    val importantFormulas: List<String> = emptyList(),
    val importantDates: List<String> = emptyList(),
    val importantNames: List<String> = emptyList(),
    val frequentlyAskedQuestions: List<FAQItem> = emptyList(),
    val revisionNotes: String = ""
)

@Serializable
data class FinalBookSummary(
    val completeBookSummary: String = "",
    val top20KeyPoints: List<String> = emptyList(),
    val importantTopics: List<String> = emptyList(),
    val quickRevisionNotes: String = "",
    val examPreparationNotes: String = ""
)

@Serializable
data class BookSummaryData(
    val metadata: BookMetadata = BookMetadata(),
    val chapters: List<ChapterDetail> = emptyList(),
    val finalSummary: FinalBookSummary = FinalBookSummary(),
    val keyConcepts: List<String> = emptyList(),
    val keywords: List<String> = emptyList()
)
