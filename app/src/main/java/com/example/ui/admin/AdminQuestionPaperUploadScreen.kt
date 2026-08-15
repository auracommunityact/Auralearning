package com.example.ui.admin

import android.net.Uri
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.example.data.models.QuestionPaper
import com.example.data.repository.AuraRepository
import kotlinx.coroutines.launch
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminQuestionPaperUploadScreen(navController: NavController) {
    val context = LocalContext.current
    val repository = remember { AuraRepository() }
    val scope = rememberCoroutineScope()

    var title by remember { mutableStateOf("") }
    var className by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var board by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("") }
    var totalPages by remember { mutableStateOf("") }
    var fileSize by remember { mutableStateOf("") }
    
    var thumbnailUrl by remember { mutableStateOf("") }
    var pdfUrl by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var selectedPdfUri by remember { mutableStateOf<Uri?>(null) }
    
    var isUploading by remember { mutableStateOf(false) }
    var sectionsFromDb by remember { mutableStateOf<List<com.example.data.models.QuestionPaperSection>>(emptyList()) }
    var expandedSection by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        scope.launch {
            sectionsFromDb = repository.getQuestionPaperSections().filter { it.isActive }
            if (sectionsFromDb.isNotEmpty() && className.isEmpty()) {
                className = sectionsFromDb.firstOrNull()?.name ?: ""
            }
        }
    }

    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { selectedPdfUri = it }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Upload Question Paper") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
                .fillMaxSize()
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Paper Title (e.g., Maths Standard 2023)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(modifier = Modifier.weight(1f)) {
                    ExposedDropdownMenuBox(
                        expanded = expandedSection,
                        onExpandedChange = { expandedSection = !expandedSection }
                    ) {
                        OutlinedTextField(
                            value = className,
                            onValueChange = { className = it },
                            label = { Text("Section/Class") },
                            modifier = Modifier.fillMaxWidth().menuAnchor(),
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSection) },
                            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                        )
                        
                        ExposedDropdownMenu(
                            expanded = expandedSection,
                            onDismissRequest = { expandedSection = false }
                        ) {
                            sectionsFromDb.forEach { section ->
                                DropdownMenuItem(
                                    text = { Text(section.name) },
                                    onClick = {
                                        className = section.name
                                        expandedSection = false
                                    }
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = subject,
                    onValueChange = { subject = it },
                    label = { Text("Subject") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = board,
                    onValueChange = { board = it },
                    label = { Text("Board (RBSE/CBSE)") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Year") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = totalPages,
                    onValueChange = { totalPages = it },
                    label = { Text("Total Pages") },
                    modifier = Modifier.weight(1f)
                )
                OutlinedTextField(
                    value = fileSize,
                    onValueChange = { fileSize = it },
                    label = { Text("File Size") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(24.dp))

            // Thumbnail Picker
            ImagePickerSection(
                title = "Thumbnail Image",
                selectedImageUri = selectedImageUri,
                onImageSelected = { selectedImageUri = it },
                existingImageUrl = thumbnailUrl
            )
            Spacer(Modifier.height(24.dp))

            // PDF Picker
            Text("PDF File", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { pdfPicker.launch("application/pdf") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (selectedPdfUri == null && pdfUrl.isEmpty()) "Select PDF File" else "PDF Selected")
            }
            if (selectedPdfUri != null || pdfUrl.isNotEmpty()) {
                Text(
                    text = if (selectedPdfUri != null) "Selected: ${selectedPdfUri!!.path}" else "Existing: $pdfUrl",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(32.dp))

            Button(
                onClick = {
                    if (title.isBlank() || className.isBlank() || subject.isBlank() || (pdfUrl.isBlank() && selectedPdfUri == null)) {
                        Toast.makeText(context, "Please fill all required fields", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    scope.launch {
                        isUploading = true
                        try {
                            var finalImageUrl = thumbnailUrl
                            if (selectedImageUri != null) {
                                val bytes = com.example.utils.StorageUtils.compressImage(context, selectedImageUri!!)
                                if (bytes != null) {
                                    val fileName = "qp_thumb_${UUID.randomUUID()}.jpg"
                                    val uploadedUrl = repository.uploadImage(bytes, fileName, "thumbnails")
                                    if (uploadedUrl.isNotEmpty()) finalImageUrl = uploadedUrl
                                }
                            }

                            var finalPdfUrl = pdfUrl
                            if (selectedPdfUri != null) {
                                val inputStream = context.contentResolver.openInputStream(selectedPdfUri!!)
                                val bytes = inputStream?.readBytes()
                                inputStream?.close()
                                if (bytes != null) {
                                    val fileName = "qp_pdf_${UUID.randomUUID()}.pdf"
                                    val uploadedUrl = repository.uploadBookPdf(bytes, fileName) // Reusing existing PDF upload
                                    if (uploadedUrl.isNotEmpty()) finalPdfUrl = uploadedUrl
                                }
                            }

                            val paper = QuestionPaper(
                                title = title,
                                className = className,
                                subject = subject,
                                board = board,
                                year = year,
                                totalPages = totalPages.toIntOrNull() ?: 0,
                                fileSize = fileSize,
                                thumbnail = finalImageUrl,
                                pdfUrl = finalPdfUrl,
                                createdAt = System.currentTimeMillis()
                            )
                            repository.addQuestionPaper(paper)
                            Toast.makeText(context, "Question Paper uploaded!", Toast.LENGTH_SHORT).show()
                            navController.popBackStack()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            isUploading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                enabled = !isUploading
            ) {
                if (isUploading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text("Upload Question Paper", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
