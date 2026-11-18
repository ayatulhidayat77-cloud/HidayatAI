package com.example.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.ProductivityNote
import com.example.data.ProductivityRepository
import com.example.data.ScheduleCategory
import com.example.data.ScheduleItem
import com.example.data.SchedulePriority
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.firestore.FirebaseFirestoreException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

sealed interface DataUiState<out T> {
    data object Loading : DataUiState<Nothing>
    data class Success<T>(val data: T) : DataUiState<T>
    data class Error(val message: String) : DataUiState<Nothing>
}

sealed interface AutoScheduleUiState {
    data object Idle : AutoScheduleUiState
    data object Loading : AutoScheduleUiState
    data class Success(val summaryBriefing: String, val addedCount: Int) : AutoScheduleUiState
    data class Error(val message: String) : AutoScheduleUiState
}

class ProductivityViewModel(
    private val repository: ProductivityRepository,
    private val currentUserId: String
) : ViewModel() {

    private val _selectedDateStr = MutableStateFlow(todayDateString())
    val selectedDateStr: StateFlow<String> = _selectedDateStr.asStateFlow()

    private val _mutationBanner = MutableStateFlow<String?>(null)
    val mutationBanner: StateFlow<String?> = _mutationBanner.asStateFlow()

    private val _autoScheduleState = MutableStateFlow<AutoScheduleUiState>(AutoScheduleUiState.Idle)
    val autoScheduleState: StateFlow<AutoScheduleUiState> = _autoScheduleState.asStateFlow()

    private val _dailyBriefingText = MutableStateFlow(
        "Ketuk 'Buat Briefing AI' untuk menyusun strategi fokus, prioritas energi, dan saran eksekusi jadwal Anda hari ini."
    )
    val dailyBriefingText: StateFlow<String> = _dailyBriefingText.asStateFlow()

    private val _isGeneratingBriefing = MutableStateFlow(false)
    val isGeneratingBriefing: StateFlow<Boolean> = _isGeneratingBriefing.asStateFlow()

    // Schema.obj for structured JSON auto-scheduling
    private val autoScheduleSchema by lazy {
        val taskItemSchema = Schema.obj(
            properties = mapOf(
                "title" to Schema.string(description = "Clear action-oriented task title in Indonesian (max 80 chars)"),
                "description" to Schema.string(description = "Concrete step-by-step execution tip in Indonesian"),
                "category" to Schema.enumeration(
                    values = listOf("WORK", "STUDY", "HEALTH", "PERSONAL", "CREATIVE"),
                    description = "Activity category"
                ),
                "priority" to Schema.enumeration(
                    values = listOf("HIGH", "MEDIUM", "LOW"),
                    description = "Priority level"
                ),
                "timeSlot" to Schema.string(description = "Time slot window in 24h format, e.g. 08:00 - 09:30")
            )
        )
        Schema.obj(
            properties = mapOf(
                "summaryBriefing" to Schema.string(
                    description = "Motivational and strategic productivity briefing in Indonesian explaining why this schedule works"
                ),
                "tasks" to Schema.array(
                    items = taskItemSchema,
                    description = "Ordered list of 3 to 6 structured daily schedule tasks"
                )
            )
        )
    }

    // Lazy initialization of Firebase.ai generativeModel for structured auto-scheduling
    private val autoSchedulerModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = autoScheduleSchema
            }
        )
    }

    // Lazy initialization for Daily Briefing generator
    private val briefingModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    // Two-tier Kotlin Flow Error Handling for Schedules
    val schedulesState: StateFlow<DataUiState<List<ScheduleItem>>> =
        repository.observeSchedules(currentUserId)
            .map<List<ScheduleItem>, DataUiState<List<ScheduleItem>>> { DataUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing schedules", rootError)
                emit(DataUiState.Error(rootError.message ?: "Gagal memuat jadwal dari cloud."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DataUiState.Loading
            )

    // Two-tier Kotlin Flow Error Handling for Notes
    val notesState: StateFlow<DataUiState<List<ProductivityNote>>> =
        repository.observeNotes(currentUserId)
            .map<List<ProductivityNote>, DataUiState<List<ProductivityNote>>> { DataUiState.Success(it) }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val rootError = firestoreError ?: error
                Log.w(TAG, "Error observing notes", rootError)
                emit(DataUiState.Error(rootError.message ?: "Gagal memuat catatan dari cloud."))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
                initialValue = DataUiState.Loading
            )

    fun selectDate(dateStr: String) {
        _selectedDateStr.value = dateStr
    }

    fun clearBanner() {
        _mutationBanner.value = null
    }

    fun resetAutoScheduleState() {
        _autoScheduleState.value = AutoScheduleUiState.Idle
    }

    fun generateAutoSchedule(userGoalPrompt: String, targetDate: String = _selectedDateStr.value) {
        if (userGoalPrompt.isBlank()) {
            _autoScheduleState.value = AutoScheduleUiState.Error("Masukkan target atau rencana aktivitas Anda terlebih dahulu.")
            return
        }
        viewModelScope.launch {
            _autoScheduleState.value = AutoScheduleUiState.Loading
            try {
                val fullPrompt = """
                    Anda adalah Hidayat AI, sistem penjadwalan otomatis eksekutif untuk produktivitas maksimal.
                    Susun jadwal harian yang seimbang, realistis, dan terstruktur dalam Bahasa Indonesia untuk tanggal $targetDate
                    berdasarkan permintaan pengguna berikut:
                    "$userGoalPrompt"
                    Pastikan waktu (timeSlot) berurutan dengan jeda istirahat yang sehat dan prioritas yang tepat.
                """.trimIndent()

                val response = withTimeout(60_000L) {
                    autoSchedulerModel.generateContent(fullPrompt)
                }
                val jsonString = response.text
                if (jsonString.isNullOrBlank()) {
                    _autoScheduleState.value = AutoScheduleUiState.Error("Model AI mengembalikan respons kosong.")
                    return@launch
                }

                // Direct JSON parsing without regex or markdown fence stripping
                val json = JSONObject(jsonString)
                val briefing = json.optString(
                    "summaryBriefing",
                    "Jadwal otomatis berhasil disusun oleh Hidayat AI."
                )
                val tasksArray = json.getJSONArray("tasks")
                var savedCount = 0

                for (i in 0 until tasksArray.length()) {
                    val itemObj = tasksArray.getJSONObject(i)
                    val title = itemObj.optString("title", "Aktivitas AI #${i + 1}")
                    val description = itemObj.optString("description", "")
                    val category = ScheduleCategory.fromRaw(itemObj.optString("category", "WORK")).name
                    val priority = SchedulePriority.fromRaw(itemObj.optString("priority", "MEDIUM")).name
                    val timeSlot = itemObj.optString("timeSlot", "08:00 - 09:00")

                    val res = repository.addSchedule(
                        userId = currentUserId,
                        title = title,
                        description = description,
                        category = category,
                        priority = priority,
                        timeSlot = timeSlot,
                        dateStr = targetDate,
                        aiGenerated = true
                    )
                    if (res.isSuccess) {
                        savedCount++
                    }
                }

                _dailyBriefingText.value = briefing
                _autoScheduleState.value = AutoScheduleUiState.Success(briefing, savedCount)
                _mutationBanner.value = "$savedCount jadwal otomatis AI berhasil disimpan ke Cloud!"
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Auto-schedule generation timed out", e)
                _autoScheduleState.value = AutoScheduleUiState.Error(
                    "Penyusunan jadwal melewati batas waktu 60 detik. Silakan coba lagi."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to generate auto schedule", e)
                _autoScheduleState.value = AutoScheduleUiState.Error(
                    e.localizedMessage ?: "Gagal menyusun jadwal otomatis AI."
                )
            }
        }
    }

    fun generateDailyBriefing(currentTasks: List<ScheduleItem>) {
        viewModelScope.launch {
            _isGeneratingBriefing.value = true
            try {
                val tasksSummary = if (currentTasks.isEmpty()) {
                    "Belum ada jadwal spesifik tercatat hari ini."
                } else {
                    currentTasks.joinToString("\n") {
                        "- [${it.timeSlot}] ${it.title} (${it.priority}, Kategori: ${it.category}, Selesai: ${it.completed})"
                    }
                }
                val prompt = """
                    Anda adalah Hidayat AI, pelatih produktivitas eksekutif.
                    Berikan briefing singkat, padat, dan memotivasi (3-4 kalimat dalam Bahasa Indonesia) untuk membantu pengguna memaksimalkan hari ini berdasarkan daftar jadwal berikut:
                    $tasksSummary
                    Fokus pada manajemen energi, tugas prioritas utama (MIT - Most Important Task), dan keseimbangan kerja.
                """.trimIndent()

                val response = withTimeout(60_000L) {
                    briefingModel.generateContent(prompt)
                }
                val text = response.text
                if (!text.isNullOrBlank()) {
                    _dailyBriefingText.value = text.trim()
                } else {
                    _mutationBanner.value = "Model AI mengembalikan briefing kosong."
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Briefing timed out", e)
                _mutationBanner.value = "Pembuatan briefing melewati batas waktu 60 detik."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Briefing generation failed", e)
                _mutationBanner.value = e.localizedMessage ?: "Gagal membuat briefing AI."
            } finally {
                _isGeneratingBriefing.value = false
            }
        }
    }

    fun addManualSchedule(
        title: String,
        description: String,
        category: String,
        priority: String,
        timeSlot: String,
        dateStr: String = _selectedDateStr.value
    ) {
        viewModelScope.launch {
            val result = repository.addSchedule(
                userId = currentUserId,
                title = title,
                description = description,
                category = category,
                priority = priority,
                timeSlot = timeSlot,
                dateStr = dateStr,
                aiGenerated = false
            )
            result.onSuccess {
                _mutationBanner.value = "Jadwal berhasil ditambahkan."
            }.onFailure { e ->
                _mutationBanner.value = e.localizedMessage ?: "Gagal menyimpan jadwal."
            }
        }
    }

    fun toggleScheduleCompleted(scheduleId: String, completed: Boolean) {
        viewModelScope.launch {
            val result = repository.toggleScheduleCompleted(currentUserId, scheduleId, completed)
            result.onFailure { e ->
                _mutationBanner.value = e.localizedMessage ?: "Gagal memperbarui status jadwal."
            }
        }
    }

    fun deleteSchedule(scheduleId: String) {
        viewModelScope.launch {
            val result = repository.deleteSchedule(currentUserId, scheduleId)
            result.onSuccess {
                _mutationBanner.value = "Jadwal dihapus."
            }.onFailure { e ->
                _mutationBanner.value = e.localizedMessage ?: "Gagal menghapus jadwal."
            }
        }
    }

    fun addNote(title: String, content: String, tag: String) {
        viewModelScope.launch {
            val result = repository.addNote(currentUserId, title, content, tag)
            result.onSuccess {
                _mutationBanner.value = "Catatan berhasil disimpan ke Cloud."
            }.onFailure { e ->
                _mutationBanner.value = e.localizedMessage ?: "Gagal menyimpan catatan."
            }
        }
    }

    fun deleteNote(noteId: String) {
        viewModelScope.launch {
            val result = repository.deleteNote(currentUserId, noteId)
            result.onSuccess {
                _mutationBanner.value = "Catatan dihapus."
            }.onFailure { e ->
                _mutationBanner.value = e.localizedMessage ?: "Gagal menghapus catatan."
            }
        }
    }

    companion object {
        private const val TAG = "ProductivityVM"
        private const val STOP_TIMEOUT_MILLIS = 5000L

        fun todayDateString(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            return sdf.format(Date())
        }

        fun provideFactory(
            repository: ProductivityRepository,
            currentUserId: String
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ProductivityViewModel(repository, currentUserId)
            }
        }
    }
}
