package com.example.viewmodel

import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.ImagePart
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

enum class AssistantMode(
    val title: String,
    val subtitle: String,
    val modelId: String,
    val isDeepThinking: Boolean
) {
    FAST(
        title = "Cepat (Flash-Lite)",
        subtitle = "Respons instan untuk tanya-jawab singkat",
        modelId = "gemini-3.5-flash-lite",
        isDeepThinking = false
    ),
    STANDARD(
        title = "Serbaguna (Flash 3.8)",
        subtitle = "Keseimbangan kecepatan & kecerdasan",
        modelId = "gemini-3.8-flash",
        isDeepThinking = false
    ),
    DEEP_THINKING(
        title = "Penalaran Tinggi (Argon 4)",
        subtitle = "Mode berpikir mendalam untuk analisis kompleks",
        modelId = "gemini-4-argon",
        isDeepThinking = true
    )
}

enum class ImageStudioModel(val label: String, val modelId: String) {
    NANO_BANANA(
        label = "Nano Banana 2.1 (Cepat & Efisien)",
        modelId = "gemini-nano-banana-2.1"
    ),
    PRO_IMAGE(
        label = "Gemini 3 Pro Image (Kualitas Studio)",
        modelId = "gemini-3-pro-image"
    )
}

enum class StudioAspectRatio(val label: String, val ratioPromptHint: String) {
    RATIO_1_1("1:1", "rasio persegi 1:1 (square)"),
    RATIO_2_3("2:3", "rasio potret 2:3"),
    RATIO_3_2("3:2", "rasio lanskap 3:2"),
    RATIO_3_4("3:4", "rasio potret 3:4"),
    RATIO_4_3("4:3", "rasio lanskap 4:3"),
    RATIO_9_16("9:16", "rasio vertikal cerita 9:16"),
    RATIO_16_9("16:9", "rasio layar lebar sinematik 16:9"),
    RATIO_21_9("21:9", "rasio ultra-wide sinematik 21:9")
}

enum class StudioImageSize(val label: String, val resolutionHint: String) {
    SIZE_1K("1K", "resolusi standar 1K (1024px)"),
    SIZE_2K("2K", "resolusi tinggi 2K (2048px)"),
    SIZE_4K("4K", "resolusi ultra-HD 4K (4096px)")
}

data class ChatTurn(
    val id: String = UUID.randomUUID().toString(),
    val isUser: Boolean,
    val text: String,
    val modeLabel: String,
    val isStreaming: Boolean = false
)

sealed interface VisionUiState {
    data object Idle : VisionUiState
    data object Loading : VisionUiState
    data class Success(val analysisText: String) : VisionUiState
    data class Error(val message: String) : VisionUiState
}

sealed interface ImageGenUiState {
    data object Idle : ImageGenUiState
    data object Loading : ImageGenUiState
    data class Success(val bitmap: Bitmap, val promptUsed: String) : ImageGenUiState
    data class Error(val message: String) : ImageGenUiState
}

class AiAssistantViewModel : ViewModel() {

    private val systemRoleInstruction = content("system") {
        text(
            "Anda adalah Hidayat AI, asisten pribadi eksekutif dan konsultan produktivitas pintar. " +
                "Bantu pengguna menyusun jadwal, memecahkan masalah kompleks, menganalisis dokumen/gambar, " +
                "dan meningkatkan fokus kerja dengan bahasa Indonesia yang jelas, terstruktur, dan solutif."
        )
    }

    // Lazy initialization for each supported Gemini chat/reasoning model
    private val fastModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AssistantMode.FAST.modelId,
            systemInstruction = systemRoleInstruction
        )
    }

    private val standardModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AssistantMode.STANDARD.modelId,
            systemInstruction = systemRoleInstruction
        )
    }

    private val deepThinkingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = AssistantMode.DEEP_THINKING.modelId,
            systemInstruction = systemRoleInstruction
        )
    }

    // Lazy initialization for Vision Analysis with gemini-4-argon
    private val visionArgonModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon"
        )
    }

    // Lazy initialization for Image Generation & Editing models
    private val nanoBananaImageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ImageStudioModel.NANO_BANANA.modelId,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val proImageModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ImageStudioModel.PRO_IMAGE.modelId,
            generationConfig = generationConfig {
                responseModalities = listOf(ResponseModality.IMAGE)
            }
        )
    }

    private val _selectedMode = MutableStateFlow(AssistantMode.STANDARD)
    val selectedMode: StateFlow<AssistantMode> = _selectedMode.asStateFlow()

    private val _chatMessages = MutableStateFlow(
        listOf(
            ChatTurn(
                isUser = false,
                text = "Halo! Saya **Hidayat AI**, asisten produktivitas pintar Anda. Anda dapat memilih mode **Cepat**, **Serbaguna**, atau **Penalaran Tinggi (Gemini 4 Argon)** untuk diskusi mendalam, strategi kerja, dan pemecahan masalah.",
                modeLabel = AssistantMode.STANDARD.title
            )
        )
    )
    val chatMessages: StateFlow<List<ChatTurn>> = _chatMessages.asStateFlow()

    private val _isChatGenerating = MutableStateFlow(false)
    val isChatGenerating: StateFlow<Boolean> = _isChatGenerating.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    // Vision analysis state
    private val _selectedVisionBitmap = MutableStateFlow<Bitmap?>(null)
    val selectedVisionBitmap: StateFlow<Bitmap?> = _selectedVisionBitmap.asStateFlow()

    private val _visionState = MutableStateFlow<VisionUiState>(VisionUiState.Idle)
    val visionState: StateFlow<VisionUiState> = _visionState.asStateFlow()

    // Image Studio state
    private val _selectedImageModel = MutableStateFlow(ImageStudioModel.NANO_BANANA)
    val selectedImageModel: StateFlow<ImageStudioModel> = _selectedImageModel.asStateFlow()

    private val _selectedAspectRatio = MutableStateFlow(StudioAspectRatio.RATIO_1_1)
    val selectedAspectRatio: StateFlow<StudioAspectRatio> = _selectedAspectRatio.asStateFlow()

    private val _selectedImageSize = MutableStateFlow(StudioImageSize.SIZE_1K)
    val selectedImageSize: StateFlow<StudioImageSize> = _selectedImageSize.asStateFlow()

    private val _editSourceBitmap = MutableStateFlow<Bitmap?>(null)
    val editSourceBitmap: StateFlow<Bitmap?> = _editSourceBitmap.asStateFlow()

    private val _imageGenState = MutableStateFlow<ImageGenUiState>(ImageGenUiState.Idle)
    val imageGenState: StateFlow<ImageGenUiState> = _imageGenState.asStateFlow()

    fun setAssistantMode(mode: AssistantMode) {
        _selectedMode.value = mode
    }

    fun clearChatError() {
        _chatError.value = null
    }

    fun sendChatMessage(userPrompt: String) {
        val cleanPrompt = userPrompt.trim()
        if (cleanPrompt.isEmpty() || _isChatGenerating.value) return

        val mode = _selectedMode.value
        val activeModel = when (mode) {
            AssistantMode.FAST -> fastModel
            AssistantMode.STANDARD -> standardModel
            AssistantMode.DEEP_THINKING -> deepThinkingModel
        }

        // Snapshot existing history for multi-turn startChat
        val historyContents: List<Content> = _chatMessages.value.map { turn ->
            content(if (turn.isUser) "user" else "model") {
                text(turn.text)
            }
        }

        val userTurn = ChatTurn(
            isUser = true,
            text = cleanPrompt,
            modeLabel = mode.title
        )
        val placeholderBotId = UUID.randomUUID().toString()
        val botPlaceholder = ChatTurn(
            id = placeholderBotId,
            isUser = false,
            text = "",
            modeLabel = mode.title,
            isStreaming = true
        )

        _chatMessages.value = _chatMessages.value + userTurn + botPlaceholder
        _isChatGenerating.value = true
        _chatError.value = null

        viewModelScope.launch {
            val accumulated = StringBuilder()
            try {
                val effectivePrompt = if (mode.isDeepThinking) {
                    "[Mode Penalaran Tinggi Aktif - Lakukan analisis langkah demi langkah secara mendalam sebelum memberikan kesimpulan taktis]\n$cleanPrompt"
                } else {
                    cleanPrompt
                }

                val chat = activeModel.startChat(history = historyContents)
                withTimeout(60_000L) {
                    chat.sendMessageStream(effectivePrompt).collect { chunk ->
                        val partText = chunk.text.orEmpty()
                        accumulated.append(partText)
                        updateStreamingTurn(placeholderBotId, accumulated.toString(), isStreaming = true)
                    }
                }

                val finalReply = accumulated.toString().trim()
                if (finalReply.isNotEmpty()) {
                    updateStreamingTurn(placeholderBotId, finalReply, isStreaming = false)
                } else {
                    removeTurn(placeholderBotId)
                    _chatError.value = "Model mengembalikan respons kosong."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Chat streaming timed out", e)
                if (accumulated.isNotEmpty()) {
                    updateStreamingTurn(placeholderBotId, accumulated.toString(), isStreaming = false)
                } else {
                    removeTurn(placeholderBotId)
                }
                _chatError.value = "Respons asisten melewati batas waktu 60 detik."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Chat generation failed", e)
                removeTurn(placeholderBotId)
                _chatError.value = e.localizedMessage ?: "Gagal menghubungi Hidayat AI."
            } finally {
                _isChatGenerating.value = false
            }
        }
    }

    private fun updateStreamingTurn(turnId: String, newText: String, isStreaming: Boolean) {
        _chatMessages.value = _chatMessages.value.map { turn ->
            if (turn.id == turnId) turn.copy(text = newText, isStreaming = isStreaming) else turn
        }
    }

    private fun removeTurn(turnId: String) {
        _chatMessages.value = _chatMessages.value.filterNot { it.id == turnId }
    }

    // Vision Image Analysis with gemini-4-argon
    fun setVisionBitmap(bitmap: Bitmap?) {
        _selectedVisionBitmap.value = bitmap
        if (bitmap == null) {
            _visionState.value = VisionUiState.Idle
        }
    }

    fun analyzeImageWithArgon(prompt: String) {
        val bitmap = _selectedVisionBitmap.value
        if (bitmap == null) {
            _visionState.value = VisionUiState.Error("Pilih foto atau gambar dokumen terlebih dahulu.")
            return
        }
        val cleanPrompt = prompt.trim().ifEmpty {
            "Analisis gambar ini secara mendetail, ekstrak poin penting, jadwal, atau langkah tindakan produktif dalam Bahasa Indonesia."
        }

        viewModelScope.launch {
            _visionState.value = VisionUiState.Loading
            try {
                val response = withTimeout(60_000L) {
                    visionArgonModel.generateContent(
                        content {
                            image(bitmap)
                            text(cleanPrompt)
                        }
                    )
                }
                val text = response.text
                if (!text.isNullOrBlank()) {
                    _visionState.value = VisionUiState.Success(text.trim())
                } else {
                    _visionState.value = VisionUiState.Error("Model Gemini 4 Argon tidak mengembalikan teks analisis.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Vision analysis timed out", e)
                _visionState.value = VisionUiState.Error("Analisis gambar melewati batas waktu 60 detik.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Vision analysis failed", e)
                _visionState.value = VisionUiState.Error(e.localizedMessage ?: "Analisis gambar gagal.")
            }
        }
    }

    // Image Studio (Text-to-Image & Image Editing with Nano Banana 2.1 / Gemini 3 Pro Image)
    fun setImageStudioModel(model: ImageStudioModel) {
        _selectedImageModel.value = model
    }

    fun setStudioAspectRatio(ratio: StudioAspectRatio) {
        _selectedAspectRatio.value = ratio
    }

    fun setStudioImageSize(size: StudioImageSize) {
        _selectedImageSize.value = size
    }

    fun setEditSourceBitmap(bitmap: Bitmap?) {
        _editSourceBitmap.value = bitmap
    }

    fun generateOrEditImage(prompt: String) {
        val cleanPrompt = prompt.trim()
        if (cleanPrompt.isEmpty()) {
            _imageGenState.value = ImageGenUiState.Error("Masukkan deskripsi gambar atau instruksi edit terlebih dahulu.")
            return
        }

        val modelChoice = _selectedImageModel.value
        val aspectRatio = _selectedAspectRatio.value
        val imageSize = _selectedImageSize.value
        val sourceBitmap = _editSourceBitmap.value

        val activeModel = when (modelChoice) {
            ImageStudioModel.NANO_BANANA -> nanoBananaImageModel
            ImageStudioModel.PRO_IMAGE -> proImageModel
        }

        val enrichedPrompt = buildString {
            append(cleanPrompt)
            append("\n[Format Visual: ")
            append(aspectRatio.ratioPromptHint)
            append(", ")
            append(imageSize.resolutionHint)
            append("]")
        }

        viewModelScope.launch {
            _imageGenState.value = ImageGenUiState.Loading
            try {
                val bitmapResult: Bitmap? = withTimeout(60_000L) {
                    val response = if (sourceBitmap != null) {
                        activeModel.generateContent(
                            content {
                                image(sourceBitmap)
                                text(enrichedPrompt)
                            }
                        )
                    } else {
                        activeModel.generateContent(enrichedPrompt)
                    }

                    response.candidates.firstOrNull()
                        ?.content?.parts?.filterIsInstance<ImagePart>()
                        ?.firstOrNull()?.image
                }

                if (bitmapResult != null) {
                    _imageGenState.value = ImageGenUiState.Success(bitmapResult, cleanPrompt)
                } else {
                    _imageGenState.value = ImageGenUiState.Error("Model tidak mengembalikan gambar.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Image generation timed out", e)
                _imageGenState.value = ImageGenUiState.Error("Pembuatan gambar melewati batas waktu 60 detik.")
            } catch (e: QuotaExceededException) {
                Log.e(TAG, "Image generation quota exceeded", e)
                _imageGenState.value = ImageGenUiState.Error("Kuota pembuatan gambar terlampaui. Silakan coba lagi nanti.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Image generation failed", e)
                _imageGenState.value = ImageGenUiState.Error(e.localizedMessage ?: "Gagal membuat gambar AI.")
            }
        }
    }

    companion object {
        private const val TAG = "AiAssistantVM"
    }
}
