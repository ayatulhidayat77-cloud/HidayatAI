package com.example.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.theme.AmberGold
import com.example.viewmodel.AiAssistantViewModel
import com.example.viewmodel.ImageGenUiState
import com.example.viewmodel.ImageStudioModel
import com.example.viewmodel.StudioAspectRatio
import androidx.compose.material3.ExperimentalMaterial3Api
import com.example.viewmodel.StudioImageSize
import com.example.viewmodel.VisionUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioAndVisionScreen(
    assistantViewModel: AiAssistantViewModel,
    onSaveAnalysisToNotes: (title: String, content: String) -> Unit,
    onSpeakText: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            Tab(
                selected = selectedTabIndex == 0,
                onClick = { selectedTabIndex = 0 },
                text = { Text("Analisis Gambar (Argon 4)") },
                icon = {
                    Icon(
                        imageVector = Icons.Default.DocumentScanner,
                        contentDescription = null
                    )
                },
                modifier = Modifier.testTag("tab_vision_analysis")
            )
            Tab(
                selected = selectedTabIndex == 1,
                onClick = { selectedTabIndex = 1 },
                text = { Text("Studio Gambar AI") },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null
                    )
                },
                modifier = Modifier.testTag("tab_image_studio")
            )
        }

        when (selectedTabIndex) {
            0 -> VisionAnalysisTab(
                assistantViewModel = assistantViewModel,
                onSaveAnalysisToNotes = onSaveAnalysisToNotes,
                onSpeakText = onSpeakText
            )
            1 -> ImageStudioTab(
                assistantViewModel = assistantViewModel
            )
        }
    }
}

@Composable
private fun VisionAnalysisTab(
    assistantViewModel: AiAssistantViewModel,
    onSaveAnalysisToNotes: (title: String, content: String) -> Unit,
    onSpeakText: (String) -> Unit
) {
    val context = LocalContext.current
    val selectedBitmap by assistantViewModel.selectedVisionBitmap.collectAsStateWithLifecycle()
    val visionState by assistantViewModel.visionState.collectAsStateWithLifecycle()

    var visionPrompt by rememberSaveable {
        mutableStateOf("Ekstrak jadwal, daftar tugas, dan ringkasan poin penting dari gambar ini.")
    }

    // Zero-permission Android Photo Picker per Google Play Policy
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = decodeBitmapFromUri(context, uri)
            assistantViewModel.setVisionBitmap(bmp)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(id = R.string.vision_analyze_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Unggah foto catatan papan tulis, jadwal cetak, atau dokumen kerja untuk dianalisis menggunakan model Gemini 4 Argon.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                if (selectedBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(210.dp)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        Image(
                            bitmap = selectedBitmap!!.asImageBitmap(),
                            contentDescription = "Gambar yang dipilih untuk dianalisis",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                OutlinedButton(
                    onClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pick_vision_photo_button"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PhotoLibrary,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (selectedBitmap == null) "Pilih Foto / Dokumen" else "Ganti Foto / Dokumen")
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = visionPrompt,
                    onValueChange = { visionPrompt = it },
                    label = { Text("Instruksi Analisis ke Gemini 4 Argon") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vision_prompt_input"),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                val isLoading = visionState is VisionUiState.Loading

                Button(
                    onClick = { assistantViewModel.analyzeImageWithArgon(visionPrompt) },
                    enabled = !isLoading && selectedBitmap != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("analyze_image_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Menganalisis dengan Gemini 4 Argon...")
                    } else {
                        Icon(imageVector = Icons.Default.DocumentScanner, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Analisis Gambar Sekarang", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        when (val state = visionState) {
            is VisionUiState.Idle -> Unit
            is VisionUiState.Loading -> Unit
            is VisionUiState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is VisionUiState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Hasil Analisis Gemini 4 Argon",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Row {
                                IconButton(
                                    onClick = { onSpeakText(state.analysisText) }
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RecordVoiceOver,
                                        contentDescription = "Bacakan hasil analisis"
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        onSaveAnalysisToNotes(
                                            "Analisis Visual Gemini 4 Argon",
                                            state.analysisText
                                        )
                                    },
                                    modifier = Modifier.testTag("save_vision_note_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.BookmarkAdd,
                                        contentDescription = "Simpan ke Catatan Cloud"
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = state.analysisText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ImageStudioTab(
    assistantViewModel: AiAssistantViewModel
) {
    val context = LocalContext.current
    val selectedModel by assistantViewModel.selectedImageModel.collectAsStateWithLifecycle()
    val selectedAspectRatio by assistantViewModel.selectedAspectRatio.collectAsStateWithLifecycle()
    val selectedImageSize by assistantViewModel.selectedImageSize.collectAsStateWithLifecycle()
    val editSourceBitmap by assistantViewModel.editSourceBitmap.collectAsStateWithLifecycle()
    val imageGenState by assistantViewModel.imageGenState.collectAsStateWithLifecycle()

    var imagePrompt by rememberSaveable {
        mutableStateOf("Ilustrasi ruang kerja minimalis modern dengan pencahayaan hangat dan papan jadwal produktif futuristik")
    }

    val editPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            val bmp = decodeBitmapFromUri(context, uri)
            assistantViewModel.setEditSourceBitmap(bmp)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = stringResource(id = R.string.image_studio_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Buat visual papan visi (vision board), ilustrasi presentasi, atau edit foto dengan model Nano Banana 2.1 dan Gemini 3 Pro Image.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 1. Model Selector (Nano Banana 2.1 vs Gemini 3 Pro Image)
                Text(
                    text = "Model Gambar AI:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ImageStudioModel.entries.forEach { model ->
                        FilterChip(
                            selected = selectedModel == model,
                            onClick = { assistantViewModel.setImageStudioModel(model) },
                            label = { Text("${model.label} (${model.modelId})") },
                            modifier = Modifier.testTag("image_model_${model.name}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Aspect Ratio Selector (1:1, 2:3, 3:2, 3:4, 4:3, 9:16, 16:9, 21:9)
                Text(
                    text = "Rasio Aspek (Aspect Ratio):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StudioAspectRatio.entries.forEach { ratio ->
                        FilterChip(
                            selected = selectedAspectRatio == ratio,
                            onClick = { assistantViewModel.setStudioAspectRatio(ratio) },
                            label = { Text(ratio.label) },
                            modifier = Modifier.testTag("aspect_ratio_${ratio.label}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 3. Resolution Size Selector (1K, 2K, 4K)
                Text(
                    text = "Ukuran Resolusi Gambar (1K / 2K / 4K):",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StudioImageSize.entries.forEach { size ->
                        FilterChip(
                            selected = selectedImageSize == size,
                            onClick = { assistantViewModel.setStudioImageSize(size) },
                            label = { Text(size.label) },
                            modifier = Modifier.testTag("image_size_${size.label}")
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 4. Optional Source Photo for Image Editing
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            editPhotoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("pick_edit_image_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(imageVector = Icons.Default.PhotoLibrary, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (editSourceBitmap == null) {
                                "Pilih Foto Dasar (Opsional untuk Edit)"
                            } else {
                                "Ganti Foto Dasar Edit"
                            }
                        )
                    }

                    if (editSourceBitmap != null) {
                        IconButton(
                            onClick = { assistantViewModel.setEditSourceBitmap(null) }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Hapus foto dasar"
                            )
                        }
                    }
                }

                if (editSourceBitmap != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = AmberGold.copy(alpha = 0.16f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Mode Edit Gambar Aktif: Prompt Anda akan diterapkan pada foto yang dipilih.",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = imagePrompt,
                    onValueChange = { imagePrompt = it },
                    label = { Text("Prompt Deskripsi / Instruksi Edit Gambar") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("image_prompt_input"),
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp)
                )

                Spacer(modifier = Modifier.height(14.dp))

                val isGenerating = imageGenState is ImageGenUiState.Loading

                Button(
                    onClick = { assistantViewModel.generateOrEditImage(imagePrompt) },
                    enabled = !isGenerating && imagePrompt.isNotBlank(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("generate_image_button"),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Merender Gambar AI (30-60 detik)...")
                    } else {
                        Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(id = R.string.generate_image_btn),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Output Preview Card
        when (val state = imageGenState) {
            is ImageGenUiState.Idle -> Unit
            is ImageGenUiState.Loading -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Sintesis gambar AI sedang berlangsung (biasanya memakan waktu 30–60 detik)...",
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            is ImageGenUiState.Error -> {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
            is ImageGenUiState.Success -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Hasil Gambar AI (${selectedAspectRatio.label} • ${selectedImageSize.label})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Image(
                            bitmap = state.bitmap.asImageBitmap(),
                            contentDescription = state.promptUsed,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp)),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }
    }
}

private fun decodeBitmapFromUri(context: Context, uri: Uri): Bitmap? {
    return try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                decoder.isMutableRequired = true
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
        }
    } catch (_: Exception) {
        null
    }
}
