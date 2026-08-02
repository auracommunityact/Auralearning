package com.example.ui.study

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.*
import android.graphics.Matrix
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.navigation.NavController
import com.example.BuildConfig
import com.example.AppContext
import com.example.data.local.PlannerDatabase
import com.example.data.local.ResultAnalysisEntity
import com.example.data.repository.AuraRepository
import com.example.utils.ResultParser
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID

// Logging Helper
object ResultAnalysisLogger {
    private const val TAG = "AuraResultAnalysis"
    
    fun info(tag: String, msg: String) {
        Log.i(TAG, "[$tag] $msg")
    }
    
    fun error(tag: String, msg: String, tr: Throwable? = null) {
        Log.e(TAG, "[$tag] $msg", tr)
    }
}

// Structured Data Classes for Parsing
@Serializable
data class ParsedAnalysisResult(
    val studentName: String? = null,
    val rollNumber: String? = null,
    val board: String? = null,
    val className: String? = null,
    val schoolName: String? = null,
    val subjects: List<ParsedSubjectScore>? = null,
    val totalMarks: Double? = null,
    val obtainedMarks: Double? = null,
    val percentage: Double? = null,
    val grade: String? = null,
    val division: String? = null,
    val passFail: String? = null,
    val rank: String? = null,
    val gpaCgpa: String? = null,
    val performanceSummary: String? = null,
    val strengths: String? = null,
    val weaknesses: String? = null,
    val improvementSuggestions: String? = null,
    val estimatedFuturePerformance: String? = null,
    val studyRecommendations: String? = null,
    val motivationalFeedback: String? = null,
    val personalizedStudyPlan: String? = null
)

@Serializable
data class ParsedSubjectScore(
    val name: String,
    val marks: Double? = null,
    val maxMarks: Double? = null
)

// Active Preprocessing States
sealed class AnalysisStep {
    object Idle : AnalysisStep()
    object Validating : AnalysisStep()
    object Preprocessing : AnalysisStep()
    object LocalOcr : AnalysisStep()
    object Analyzing : AnalysisStep()
    object Completed : AnalysisStep()
    data class Error(val message: String) : AnalysisStep()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultAnalysisScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { AuraRepository() }

    // Screen States
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var analysisStep by remember { mutableStateOf<AnalysisStep>(AnalysisStep.Idle) }
    var parsedResult by remember { mutableStateOf<ParsedAnalysisResult?>(null) }
    var selectedTab by remember { mutableStateOf(0) }
    var showLowConfidenceWarning by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Camera Intent Variables
    var tempCameraFile by remember { mutableStateOf<File?>(null) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }

    // Launchers
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            selectedImageUri = uri
            analysisStep = AnalysisStep.Idle
            parsedResult = null
            processedBitmap = null
            showLowConfidenceWarning = false
            ResultAnalysisLogger.info("ImageSelection", "Image selected from Gallery: $uri")
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            selectedImageUri = tempCameraUri
            analysisStep = AnalysisStep.Idle
            parsedResult = null
            processedBitmap = null
            showLowConfidenceWarning = false
            ResultAnalysisLogger.info("ImageSelection", "Image captured via Camera: $tempCameraUri")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
        if (isGranted) {
            try {
                val (file, uri) = createCameraTempUri(context)
                tempCameraFile = file
                tempCameraUri = uri
                cameraLauncher.launch(uri)
            } catch (e: Exception) {
                Toast.makeText(context, "Error starting Camera: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(context, "Camera permission denied. Cannot take photo.", Toast.LENGTH_SHORT).show()
        }
    }

    // Design Colors Matching Aura Slate Dark Theme
    val bgPrimary = Color(0xFF0F172A)
    val cardBg = Color(0xFF1E293B)
    val cardBorder = Color(0xFF334155)
    val accentBlue = Color(0xFF60A5FA)
    val accentNavy = Color(0xFF1E3A8A)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "AI Result Analysis",
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bgPrimary)
            )
        },
        containerColor = bgPrimary
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(bgPrimary)
        ) {
            val configuration = LocalConfiguration.current
            val isTablet = configuration.screenWidthDp >= 600

            if (isTablet) {
                // Horizontal Two-Pane Adaptive Layout for Tablets
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Pane: Selector / Preview / Controls
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        ImagePreviewAndPickerSection(
                            selectedUri = selectedImageUri,
                            bitmap = processedBitmap,
                            onGalleryClick = { galleryLauncher.launch("image/*") },
                            onCameraClick = {
                                if (hasCameraPermission) {
                                    val (file, uri) = createCameraTempUri(context)
                                    tempCameraFile = file
                                    tempCameraUri = uri
                                    cameraLauncher.launch(uri)
                                } else {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                        )

                        ControlAndProgressSection(
                            selectedUri = selectedImageUri,
                            analysisStep = analysisStep,
                            showLowConfidenceWarning = showLowConfidenceWarning,
                            onDismissWarning = { showLowConfidenceWarning = false },
                            onStartAnalysis = {
                                coroutineScope.launch {
                                    runFullAnalysisPipeline(
                                        context = context,
                                        uri = selectedImageUri!!,
                                        repository = repository,
                                        onStepUpdate = { analysisStep = it },
                                        onBitmapReady = { processedBitmap = it },
                                        onLowConfidence = { showLowConfidenceWarning = true },
                                        onSuccess = { result ->
                                            parsedResult = result
                                            selectedTab = 0
                                        }
                                    )
                                }
                            },
                            onReset = {
                                selectedImageUri = null
                                processedBitmap = null
                                parsedResult = null
                                analysisStep = AnalysisStep.Idle
                                showLowConfidenceWarning = false
                            }
                        )
                    }

                    // Right Pane: Output Analysis Results
                    Column(
                        modifier = Modifier.weight(1.8f)
                    ) {
                        if (parsedResult != null) {
                            AnalysisResultDisplay(
                                result = parsedResult!!,
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it },
                                cardBg = cardBg,
                                accentColor = accentBlue,
                                accentNavy = accentNavy
                            )
                        } else {
                            PlaceholderDisplay(cardBg)
                        }
                    }
                }
            } else {
                // Portrait Layout for Standard Mobile Phones
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ImagePreviewAndPickerSection(
                        selectedUri = selectedImageUri,
                        bitmap = processedBitmap,
                        onGalleryClick = { galleryLauncher.launch("image/*") },
                        onCameraClick = {
                            if (hasCameraPermission) {
                                val (file, uri) = createCameraTempUri(context)
                                tempCameraFile = file
                                tempCameraUri = uri
                                cameraLauncher.launch(uri)
                            } else {
                                permissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    )

                    ControlAndProgressSection(
                        selectedUri = selectedImageUri,
                        analysisStep = analysisStep,
                        showLowConfidenceWarning = showLowConfidenceWarning,
                        onDismissWarning = { showLowConfidenceWarning = false },
                        onStartAnalysis = {
                            coroutineScope.launch {
                                runFullAnalysisPipeline(
                                    context = context,
                                    uri = selectedImageUri!!,
                                    repository = repository,
                                    onStepUpdate = { analysisStep = it },
                                    onBitmapReady = { processedBitmap = it },
                                    onLowConfidence = { showLowConfidenceWarning = true },
                                    onSuccess = { result ->
                                        parsedResult = result
                                        selectedTab = 0
                                    }
                                )
                            }
                        },
                        onReset = {
                            selectedImageUri = null
                            processedBitmap = null
                            parsedResult = null
                            analysisStep = AnalysisStep.Idle
                            showLowConfidenceWarning = false
                        }
                    )

                    if (parsedResult != null) {
                        AnalysisResultDisplay(
                            result = parsedResult!!,
                            selectedTab = selectedTab,
                            onTabSelected = { selectedTab = it },
                            cardBg = cardBg,
                            accentColor = accentBlue,
                            accentNavy = accentNavy
                        )
                    }
                }
            }
        }
    }
}

// File and URI Helper for Camera Support
private fun createCameraTempUri(context: Context): Pair<File, Uri> {
    val dir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) ?: context.filesDir
    val file = File(dir, "aura_camera_result_temp.jpg").apply {
        if (exists()) delete()
        createNewFile()
    }
    val uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.provider",
        file
    )
    return Pair(file, uri)
}

// Preprocessing & Image Manipulation
private fun scaleAndNormalizeBitmap(context: Context, uri: Uri, maxDimension: Int = 1200): Bitmap {
    ResultAnalysisLogger.info("ImagePreprocessing", "Validating & decoding bounds for: $uri")
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri).use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }

    val srcWidth = options.outWidth
    val srcHeight = options.outHeight

    if (srcWidth <= 0 || srcHeight <= 0) {
        throw IllegalArgumentException("Invalid or corrupted image file bounds.")
    }

    // Determine scale bounds
    var sampleSize = 1
    while (srcWidth / sampleSize > maxDimension || srcHeight / sampleSize > maxDimension) {
        sampleSize *= 2
    }

    val decodeOptions = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }

    var bitmap = context.contentResolver.openInputStream(uri).use { stream ->
        BitmapFactory.decodeStream(stream, null, decodeOptions)
    } ?: throw IllegalArgumentException("Could not decode image data stream.")

    // Convert HARDWARE bitmaps to SOFTWARE so we can manipulate them
    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true)
    }

    // Exact scale adjustments
    val scale = minOf(maxDimension.toFloat() / bitmap.width, maxDimension.toFloat() / bitmap.height)
    if (scale < 1f) {
        val scaledW = (bitmap.width * scale).toInt()
        val scaledH = (bitmap.height * scale).toInt()
        val scaledBmp = Bitmap.createScaledBitmap(bitmap, scaledW, scaledH, true)
        if (scaledBmp != bitmap) {
            bitmap.recycle()
            bitmap = scaledBmp
        }
    }
    return bitmap
}

// Auto rotation by checking Exif tags
private fun autoRotateBitmap(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
    return try {
        val rotationDegrees = context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
            val exif = android.media.ExifInterface(pfd.fileDescriptor)
            when (exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        } ?: 0

        if (rotationDegrees != 0) {
            ResultAnalysisLogger.info("ImagePreprocessing", "Auto-rotating image by $rotationDegrees degrees.")
            val matrix = Matrix().apply { postRotate(rotationDegrees.toFloat()) }
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated != bitmap) {
                bitmap.recycle()
                return rotated
            }
        }
        bitmap
    } catch (e: Exception) {
        ResultAnalysisLogger.error("ImagePreprocessing", "Failed to retrieve rotation metadata", e)
        bitmap
    }
}

// Apply ColorMatrix contrast and brightness adjustments to help text pop
private fun enhanceContrastAndBrightness(bitmap: Bitmap, contrast: Float = 1.4f, brightness: Float = 10f): Bitmap {
    ResultAnalysisLogger.info("ImagePreprocessing", "Applying ColorMatrix contrast ($contrast) and brightness ($brightness).")
    val cm = ColorMatrix(floatArrayOf(
        contrast, 0f, 0f, 0f, brightness,
        0f, contrast, 0f, 0f, brightness,
        0f, 0f, contrast, 0f, brightness,
        0f, 0f, 0f, 1f, 0f
    ))
    val outBitmap = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(outBitmap)
    val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(cm) }
    canvas.drawBitmap(bitmap, 0f, 0f, paint)
    bitmap.recycle()
    return outBitmap
}

// Fast pixel sharpening pass
private fun sharpenBitmap(src: Bitmap): Bitmap {
    ResultAnalysisLogger.info("ImagePreprocessing", "Applying 3x3 horizontal/vertical pixel difference sharpening.")
    val width = src.width
    val height = src.height
    val pixels = IntArray(width * height)
    src.getPixels(pixels, 0, width, 0, 0, width, height)

    val outputPixels = IntArray(width * height)

    for (y in 1 until height - 1) {
        val yOffset = y * width
        val prevY = (y - 1) * width
        val nextY = (y + 1) * width
        for (x in 1 until width - 1) {
            val idx = yOffset + x

            val pxCenter = pixels[idx]
            val pxUp = pixels[prevY + x]
            val pxDown = pixels[nextY + x]
            val pxLeft = pixels[idx - 1]
            val pxRight = pixels[idx + 1]

            val rC = (pxCenter shr 16) and 0xFF
            val gC = (pxCenter shr 8) and 0xFF
            val bC = pxCenter and 0xFF

            val r = (5 * rC - ((pxUp shr 16) and 0xFF) - ((pxDown shr 16) and 0xFF) - ((pxLeft shr 16) and 0xFF) - ((pxRight shr 16) and 0xFF)).coerceIn(0, 255)
            val g = (5 * gC - ((pxUp shr 8) and 0xFF) - ((pxDown shr 8) and 0xFF) - ((pxLeft shr 8) and 0xFF) - ((pxRight shr 8) and 0xFF)).coerceIn(0, 255)
            val b = (5 * bC - (pxUp and 0xFF) - (pxDown and 0xFF) - (pxLeft and 0xFF) - (pxRight and 0xFF)).coerceIn(0, 255)

            outputPixels[idx] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    System.arraycopy(pixels, 0, outputPixels, 0, width)
    System.arraycopy(pixels, (height - 1) * width, outputPixels, (height - 1) * width, width)
    for (y in 0 until height) {
        outputPixels[y * width] = pixels[y * width]
        outputPixels[y * width + (width - 1)] = pixels[y * width + (width - 1)]
    }

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(outputPixels, 0, width, 0, 0, width, height)
    src.recycle()
    return result
}

// Robust Dynamic JSON Parser
fun parseAuraAnalysisJson(jsonStr: String): ParsedAnalysisResult {
    return try {
        val startIndex = jsonStr.indexOf('{')
        val endIndex = jsonStr.lastIndexOf('}')
        if (startIndex == -1 || endIndex == -1) {
            throw IllegalArgumentException("Could not isolate JSON wrapper inside response block.")
        }
        val cleanJson = jsonStr.substring(startIndex, endIndex + 1)
        val json = JSONObject(cleanJson)

        val subjectsList = mutableListOf<ParsedSubjectScore>()
        val subjectsArray = json.optJSONArray("subjects")
        if (subjectsArray != null) {
            for (i in 0 until subjectsArray.length()) {
                val subObj = subjectsArray.optJSONObject(i)
                if (subObj != null) {
                    val name = subObj.optString("name", "Subject $i")
                    val marks = if (subObj.isNull("marks")) null else subObj.optDouble("marks")
                    val maxMarks = if (subObj.isNull("maxMarks")) null else subObj.optDouble("maxMarks")
                    subjectsList.add(ParsedSubjectScore(name, marks, maxMarks))
                }
            }
        }

        ParsedAnalysisResult(
            studentName = json.optString("studentName", "").takeIf { it != "null" && it.isNotEmpty() },
            rollNumber = json.optString("rollNumber", "").takeIf { it != "null" && it.isNotEmpty() },
            board = json.optString("board", "").takeIf { it != "null" && it.isNotEmpty() },
            className = json.optString("className", "").takeIf { it != "null" && it.isNotEmpty() },
            schoolName = json.optString("schoolName", "").takeIf { it != "null" && it.isNotEmpty() },
            subjects = subjectsList,
            totalMarks = if (json.isNull("totalMarks")) null else json.optDouble("totalMarks"),
            obtainedMarks = if (json.isNull("obtainedMarks")) null else json.optDouble("obtainedMarks"),
            percentage = if (json.isNull("percentage")) null else json.optDouble("percentage"),
            grade = json.optString("grade", "").takeIf { it != "null" && it.isNotEmpty() },
            division = json.optString("division", "").takeIf { it != "null" && it.isNotEmpty() },
            passFail = json.optString("passFail", "").takeIf { it != "null" && it.isNotEmpty() },
            rank = json.optString("rank", "").takeIf { it != "null" && it.isNotEmpty() },
            gpaCgpa = json.optString("gpaCgpa", "").takeIf { it != "null" && it.isNotEmpty() },
            performanceSummary = json.optString("performanceSummary", "").takeIf { it.isNotEmpty() },
            strengths = json.optString("strengths", "").takeIf { it.isNotEmpty() },
            weaknesses = json.optString("weaknesses", "").takeIf { it.isNotEmpty() },
            improvementSuggestions = json.optString("improvementSuggestions", "").takeIf { it.isNotEmpty() },
            estimatedFuturePerformance = json.optString("estimatedFuturePerformance", "").takeIf { it.isNotEmpty() },
            studyRecommendations = json.optString("studyRecommendations", "").takeIf { it.isNotEmpty() },
            motivationalFeedback = json.optString("motivationalFeedback", "").takeIf { it.isNotEmpty() },
            personalizedStudyPlan = json.optString("personalizedStudyPlan", "").takeIf { it.isNotEmpty() }
        )
    } catch (e: Exception) {
        ResultAnalysisLogger.error("ParsingError", "Failed to deserialize JSON content. Falling back to structured parsing.", e)
        ParsedAnalysisResult(
            studentName = "Analyzed Student",
            performanceSummary = jsonStr,
            motivationalFeedback = "Analysis completed. Read details below."
        )
    }
}

// Main background execution pipeline
private suspend fun runFullAnalysisPipeline(
    context: Context,
    uri: Uri,
    repository: AuraRepository,
    onStepUpdate: (AnalysisStep) -> Unit,
    onBitmapReady: (Bitmap) -> Unit,
    onLowConfidence: () -> Unit,
    onSuccess: (ParsedAnalysisResult) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            // 0. Cache Check
            val db = PlannerDatabase.getDatabase(context)
            val cached = db.resultAnalysisDao().getResultByUri(uri.toString())
            if (cached != null) {
                ResultAnalysisLogger.info("Pipeline", "Found cached result for URI.")
                onSuccess(cached.toParsedResult())
                onStepUpdate(AnalysisStep.Completed)
                return@withContext
            }

            // 1. Validation
            onStepUpdate(AnalysisStep.Validating)
            ResultAnalysisLogger.info("Pipeline", "Validating file exists and is readable.")
            var isReadable = false
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeStream(stream, null, options)
                    isReadable = options.outWidth > 0 && options.outHeight > 0
                }
            } catch (e: Exception) {
                ResultAnalysisLogger.error("Pipeline", "Validation check failed.", e)
            }

            if (!isReadable) {
                onStepUpdate(AnalysisStep.Error("Invalid image. The file is unreadable, empty, or corrupted."))
                return@withContext
            }

            // 2. Preprocessing (Resize, Rotate, Contrast, Sharpen)
            onStepUpdate(AnalysisStep.Preprocessing)
            var bmp = scaleAndNormalizeBitmap(context, uri)
            bmp = autoRotateBitmap(context, uri, bmp)
            bmp = enhanceContrastAndBrightness(bmp)
            bmp = sharpenBitmap(bmp)
            onBitmapReady(bmp)

            // 3. Local ML Kit Fast OCR
            onStepUpdate(AnalysisStep.LocalOcr)
            ResultAnalysisLogger.info("Pipeline", "Executing local GMS ML Kit fast OCR scan.")
            val inputImage = InputImage.fromBitmap(bmp, 0)
            val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            val ocrResult = recognizer.process(inputImage).await()
            val extractedText = ocrResult.text

            ResultAnalysisLogger.info("OCR", "ML Kit text extraction length: ${extractedText.length}")
            val isOcrLowConfidence = extractedText.trim().length < 30 || !extractedText.any { it.isDigit() }
            if (isOcrLowConfidence) {
                ResultAnalysisLogger.info("OCR", "Extracted text is insufficient. Low OCR confidence flagged.")
                withContext(Dispatchers.Main) { 
                    onLowConfidence()
                }
                onStepUpdate(AnalysisStep.Error("OCR could not extract enough information. Please recapture a clearer, upright photo of the marksheet."))
                return@withContext
            }

            // 4. Analysis
            onStepUpdate(AnalysisStep.Analyzing)
            
            val result = ResultParser.parse(ocrResult)

            // Cache the result
            try {
                db.resultAnalysisDao().insertResult(ResultAnalysisEntity.fromParsedResult(uri.toString(), result))
            } catch (e: Exception) {
                ResultAnalysisLogger.error("Pipeline", "Failed to cache result", e)
            }

            ResultAnalysisLogger.info("Analysis", "Successfully completed results parsing.")
            
            withContext(Dispatchers.Main) {
                onSuccess(result)
                onStepUpdate(AnalysisStep.Completed)
            }

        } catch (e: Exception) {
            ResultAnalysisLogger.error("Pipeline", "An exception occurred during analysis pipeline.", e)
            val friendlyMsg = when {
                e is java.net.UnknownHostException || e is java.net.ConnectException -> 
                    "No internet connection. Please verify your network and retry."
                e is java.io.IOException -> 
                    "Network timeout or file access error during analysis."
                else -> "Analysis failed: ${e.localizedMessage ?: "Unknown error"}"
            }
            onStepUpdate(AnalysisStep.Error(friendlyMsg))
        }
    }
}

// UI Composable Components
@Composable
fun ImagePreviewAndPickerSection(
    selectedUri: Uri?,
    bitmap: Bitmap?,
    onGalleryClick: () -> Unit,
    onCameraClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(280.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
        if (bitmap != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Selected Marksheet",
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(20.dp)),
                    contentScale = ContentScale.Fit
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        .clickable { onGalleryClick() }
                        .padding(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Refresh,
                        contentDescription = "Change Image",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        } else if (selectedUri != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF60A5FA))
                Text(
                    "Loading & Rotating...",
                    color = Color.White.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 64.dp)
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Filled.DocumentScanner,
                    contentDescription = null,
                    modifier = Modifier.size(56.dp),
                    tint = Color(0xFF60A5FA)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Upload Marksheet / Report Card",
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Accepts JPG, PNG, HEIC & WEBP formats.",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = onCameraClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E3A8A)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Camera", fontSize = 13.sp)
                    }

                    Button(
                        onClick = onGalleryClick,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Filled.Collections, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Gallery", fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ControlAndProgressSection(
    selectedUri: Uri?,
    analysisStep: AnalysisStep,
    showLowConfidenceWarning: Boolean,
    onDismissWarning: () -> Unit,
    onStartAnalysis: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (selectedUri != null && analysisStep == AnalysisStep.Idle) {
            Button(
                onClick = onStartAnalysis,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3b82f6))
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                Spacer(modifier = Modifier.width(10.dp))
                Text("Analyze Results", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        }

        // Active Pipeline Progress Reporting
        if (analysisStep !is AnalysisStep.Idle && analysisStep !is AnalysisStep.Completed) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val statusText = when (analysisStep) {
                        is AnalysisStep.Validating -> "Validating marksheet image..."
                        is AnalysisStep.Preprocessing -> "Applying filters (Sharpening & contrast)..."
                        is AnalysisStep.LocalOcr -> "Reading lines (Local fast OCR)..."
                        is AnalysisStep.Analyzing -> "Performing local analysis..."
                        is AnalysisStep.Error -> "Failed to process."
                        else -> "Processing..."
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(statusText, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        if (analysisStep !is AnalysisStep.Error) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF60A5FA), strokeWidth = 2.5.dp)
                        } else {
                            Icon(Icons.Filled.Error, contentDescription = null, tint = Color(0xFFF87171))
                        }
                    }

                    if (analysisStep !is AnalysisStep.Error) {
                        LinearProgressIndicator(
                            progress = {
                                when (analysisStep) {
                                    is AnalysisStep.Validating -> 0.2f
                                    is AnalysisStep.Preprocessing -> 0.4f
                                    is AnalysisStep.LocalOcr -> 0.7f
                                    is AnalysisStep.Analyzing -> 0.9f
                                    else -> 0.0f
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = Color(0xFF60A5FA),
                            trackColor = Color(0xFF334155)
                        )
                    }

                    if (analysisStep is AnalysisStep.Error) {
                        Text(
                            text = analysisStep.message,
                            color = Color(0xFFF87171),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                        Button(
                            onClick = onStartAnalysis,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444)),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry Analysis")
                        }
                    }
                }
            }
        }

        // Low OCR Confidence Warning Overlay/Banner
        if (showLowConfidenceWarning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF78350F).copy(alpha = 0.8f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD97706))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = "Warning", tint = Color(0xFFFBBF24), modifier = Modifier.size(24.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Low OCR Confidence",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                        Text(
                            "Text looks blurry or tilted. Gemini will try its best, but uploading a clearer upright photo is recommended.",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 11.sp,
                            lineHeight = 15.sp
                        )
                    }
                    IconButton(onClick = onDismissWarning) {
                        Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (selectedUri != null && analysisStep is AnalysisStep.Completed) {
            Button(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Icon(Icons.Filled.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Another Marksheet")
            }
        }
    }
}

@Composable
fun PlaceholderDisplay(cardBg: Color) {
    Card(
        modifier = Modifier
            .fillMaxSize()
            .heightIn(min = 400.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Analytics,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.White.copy(alpha = 0.2f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "Awaiting Analysis",
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 16.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Upload and analyze your report card image. Gemini AI will parse student data and generate deep academic counseling insights here.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }
    }
}

@Composable
fun AnalysisResultDisplay(
    result: ParsedAnalysisResult,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    cardBg: Color,
    accentColor: Color,
    accentNavy: Color
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        TabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color.Transparent,
            contentColor = accentColor,
            modifier = Modifier.fillMaxWidth()
        ) {
            Tab(
                selected = selectedTab == 0,
                onClick = { onTabSelected(0) },
                text = { Text("Overview", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 1,
                onClick = { onTabSelected(1) },
                text = { Text("AI Insights", fontWeight = FontWeight.Bold) }
            )
            Tab(
                selected = selectedTab == 2,
                onClick = { onTabSelected(2) },
                text = { Text("Study Plan", fontWeight = FontWeight.Bold) }
            )
        }

        when (selectedTab) {
            0 -> OverviewTabContent(result, cardBg, accentColor, accentNavy)
            1 -> InsightsTabContent(result, cardBg, accentColor)
            2 -> StudyPlanTabContent(result, cardBg, accentColor)
        }
    }
}

@Composable
fun SubjectPerformanceChart(subjects: List<ParsedSubjectScore>, accentColor: Color) {
    if (subjects.isEmpty()) return
    
    val maxScore = subjects.maxOfOrNull { it.maxMarks ?: 100.0 } ?: 100.0
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "PERFORMANCE TREND",
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            androidx.compose.foundation.Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            ) {
                val canvasWidth = size.width
                val canvasHeight = size.height
                val barWidth = canvasWidth / (subjects.size * 2)
                val spacing = barWidth
                
                subjects.forEachIndexed { index, subject ->
                    val scoreRatio = (subject.marks ?: 0.0) / (subject.maxMarks ?: 100.0)
                    val barHeight = canvasHeight * scoreRatio.toFloat()
                    
                    val x = spacing + index * (barWidth + spacing)
                    val y = canvasHeight - barHeight
                    
                    drawRoundRect(
                        color = accentColor,
                        topLeft = androidx.compose.ui.geometry.Offset(x, y),
                        size = androidx.compose.ui.geometry.Size(barWidth, barHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )
                    
                    // Draw subject initials
                    val initials = subject.name.take(3).uppercase()
                    // (Skipping text drawing on canvas for simplicity, using labels below)
                }
                
                // Draw baseline
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = androidx.compose.ui.geometry.Offset(0f, canvasHeight),
                    end = androidx.compose.ui.geometry.Offset(canvasWidth, canvasHeight),
                    strokeWidth = 1.dp.toPx()
                )
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                subjects.forEach { subject ->
                    Text(
                        text = subject.name.take(3).uppercase(),
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun OverviewTabContent(
    result: ParsedAnalysisResult,
    cardBg: Color,
    accentColor: Color,
    accentNavy: Color
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Student Meta Information Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(accentNavy, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Person, contentDescription = null, tint = accentColor)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = result.studentName ?: "Unknown Student",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 18.sp
                        )
                        Text(
                            text = if (result.rollNumber != null) "Roll No: ${result.rollNumber}" else "Academic Record Profile",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFF334155))

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    MetaInfoRow("School/Inst:", result.schoolName ?: "N/A")
                    MetaInfoRow("Board/Exam:", result.board ?: "N/A")
                    MetaInfoRow("Class/Grade:", result.className ?: "N/A")
                    MetaInfoRow("Division/Rank:", result.division ?: result.rank ?: "N/A")
                }
            }
        }

        // Percentage & Grade Progress Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("OVERALL PERCENTAGE", color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (result.percentage != null) String.format("%.2f%%", result.percentage) else "N/A",
                        fontWeight = FontWeight.Black,
                        color = accentColor,
                        fontSize = 28.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(result.passFail ?: "N/A", color = Color.White, fontWeight = FontWeight.Bold) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (result.passFail?.contains("Pass", true) == true) Color(0xFF065F46) else Color(0xFF991B1B)
                            ),
                            border = null
                        )
                        if (result.grade != null) {
                            Text("Grade: ${result.grade}", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                    }
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(80.dp)
                ) {
                    val progress = (result.percentage ?: 0.0).toFloat() / 100f
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.size(80.dp),
                        color = accentColor,
                        strokeWidth = 8.dp,
                        trackColor = Color(0xFF334155)
                    )
                    Text(
                        text = result.gpaCgpa?.takeIf { it.isNotEmpty() } ?: "${(result.obtainedMarks ?: 0.0).toInt()}",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 14.sp
                    )
                }
            }
        }

        if (!result.subjects.isNullOrEmpty()) {
            SubjectPerformanceChart(subjects = result.subjects, accentColor = accentColor)
        }

        // Subjects & Marks Display
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "SUBJECT-WISE MARKS",
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 12.sp
                )

                if (result.subjects.isNullOrEmpty()) {
                    Text("No direct subject scores extracted.", color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        result.subjects.forEach { subject ->
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(subject.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                    val markText = if (subject.marks != null) {
                                        "${subject.marks.toInt()} / ${subject.maxMarks?.toInt() ?: 100}"
                                    } else {
                                        "N/A"
                                    }
                                    Text(markText, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                val progressVal = if (subject.marks != null && subject.maxMarks != null && subject.maxMarks > 0) {
                                    (subject.marks / subject.maxMarks).toFloat()
                                } else if (subject.marks != null) {
                                    (subject.marks / 100f).toFloat()
                                } else {
                                    0f
                                }.coerceIn(0f, 1f)

                                LinearProgressIndicator(
                                    progress = { progressVal },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp)),
                                    color = if (progressVal >= 0.75f) Color(0xFF34D399) else if (progressVal >= 0.50f) Color(0xFFFBBF24) else Color(0xFFF87171),
                                    trackColor = Color(0xFF334155)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InsightsTabContent(result: ParsedAnalysisResult, cardBg: Color, accentColor: Color) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Performance Summary Section
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PERFORMANCE SUMMARY", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                }
                Text(
                    text = result.performanceSummary ?: "No summary provided by AI.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }

        // Strengths & Weaknesses Split Card
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("STRENGTHS", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                    }
                    Text(
                        text = result.strengths ?: "Excelling across core modules.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("WEAK AREAS", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 12.sp)
                    }
                    Text(
                        text = result.weaknesses ?: "Ready to improve.",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        lineHeight = 16.sp
                    )
                }
            }
        }

        // Motivational Feedback Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E3A8A).copy(alpha = 0.3f)),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF3B82F6).copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EmojiEvents, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("AURA'S ENCOURAGEMENT ✨", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                }
                Text(
                    text = result.motivationalFeedback ?: "You have incredible potential. Keep pushing forward!",
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun StudyPlanTabContent(result: ParsedAnalysisResult, cardBg: Color, accentColor: Color) {
    Column(
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        // Structured Study Plan Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.EventNote, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("PERSONALIZED STUDY PLAN", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                }
                Text(
                    text = result.personalizedStudyPlan ?: "Create a consistent routine to cover weak topics daily.",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    lineHeight = 20.sp
                )
            }
        }

        // Recommendations Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = cardBg),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = Color(0xFFFBBF24), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("STUDY STRATEGIES & ADVICE", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                }
                Text(
                    text = result.studyRecommendations ?: result.improvementSuggestions ?: "Set daily smart targets, resolve doubts using Aura, and revise weak chapters regularly.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        // Estimated Future Performance
        if (result.estimatedFuturePerformance != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = cardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF334155))
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = Color(0xFF34D399), modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ESTIMATED FUTURE ROADMAP", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                    }
                    Text(
                        text = result.estimatedFuturePerformance,
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun MetaInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.5f),
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp
        )
        Text(
            text = value,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 200.dp)
        )
    }
}
