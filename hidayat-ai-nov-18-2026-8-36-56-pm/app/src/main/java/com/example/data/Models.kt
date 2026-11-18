package com.example.data

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class ScheduleCategory(val label: String) {
    WORK("Pekerjaan"),
    STUDY("Belajar"),
    HEALTH("Kesehatan"),
    PERSONAL("Personal"),
    CREATIVE("Kreatif");

    companion object {
        fun fromRaw(raw: String?): ScheduleCategory =
            entries.firstOrNull { it.name == raw } ?: WORK
    }
}

enum class SchedulePriority(val label: String) {
    HIGH("Tinggi"),
    MEDIUM("Sedang"),
    LOW("Rendah");

    companion object {
        fun fromRaw(raw: String?): SchedulePriority =
            entries.firstOrNull { it.name == raw } ?: MEDIUM
    }
}

enum class NoteTag(val label: String) {
    AI_INSIGHT("Wawasan AI"),
    VISION_ANALYSIS("Analisis Visual"),
    DAILY_BRIEF("Briefing Harian"),
    IDEA("Ide & Strategi");

    companion object {
        fun fromRaw(raw: String?): NoteTag =
            entries.firstOrNull { it.name == raw } ?: AI_INSIGHT
    }
}

@Keep
@IgnoreExtraProperties
data class ScheduleItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = ScheduleCategory.WORK.name,
    val priority: String = SchedulePriority.MEDIUM.name,
    val timeSlot: String = "08:00 - 09:00",
    val dateStr: String = "",
    val completed: Boolean = false,
    val aiGenerated: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@Keep
@IgnoreExtraProperties
data class ProductivityNote(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val content: String = "",
    val tag: String = NoteTag.AI_INSIGHT.name,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)
