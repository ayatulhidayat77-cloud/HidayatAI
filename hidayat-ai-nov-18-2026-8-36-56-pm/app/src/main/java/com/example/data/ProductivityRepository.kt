package com.example.data

import android.content.Context
import com.example.R
import com.example.firebase.OperationType
import com.example.firebase.handleFirestoreError
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

class ProductivityRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    // CRITICAL: Always initialize with R.string.firestore_database_id
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(expectedUserId: String): String {
        val uid = auth.currentUser?.uid
            ?: throw IllegalStateException("Pengguna harus masuk dengan Google terlebih dahulu.")
        check(uid == expectedUserId) { "Sesi pengguna berubah; menolak penulisan lintas akun." }
        return uid
    }

    fun observeSchedules(userId: String): Flow<List<ScheduleItem>> {
        val path = "users/$userId/schedules"
        return db.collection("users").document(userId).collection("schedules")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ScheduleItem::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedWith(
                    compareBy<ScheduleItem> { it.completed }
                        .thenBy { it.timeSlot }
                        .thenByDescending { it.createdAt ?: Timestamp(0, 0) }
                )
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    fun observeNotes(userId: String): Flow<List<ProductivityNote>> {
        val path = "users/$userId/notes"
        return db.collection("users").document(userId).collection("notes")
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(
                    ProductivityNote::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
            }
            .catch { error ->
                val firestoreError = generateSequence(error) { it.cause }
                    .filterIsInstance<FirebaseFirestoreException>()
                    .firstOrNull()
                if (firestoreError == null && error is CancellationException) throw error
                val exceptionToLog = firestoreError ?: (error as? Exception)
                if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                    handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                }
                throw error
            }
    }

    suspend fun addSchedule(
        userId: String,
        title: String,
        description: String,
        category: String,
        priority: String,
        timeSlot: String,
        dateStr: String,
        aiGenerated: Boolean
    ): Result<Unit> {
        var docPath = "users/$userId/schedules"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = title.trim().take(150).ifEmpty { "Aktivitas Terjadwal" }
            val cleanDesc = description.trim().take(1000)
            val validCategory = ScheduleCategory.fromRaw(category).name
            val validPriority = SchedulePriority.fromRaw(priority).name
            val cleanTimeSlot = timeSlot.trim().take(40).ifEmpty { "09:00 - 10:00" }
            val cleanDateStr = dateStr.trim().take(30).ifEmpty { "2026-11-18" }

            val docRef = db.collection("users").document(uid).collection("schedules").document()
            docPath = docRef.path
            // Exact keyset matching firestore.rules isValidScheduleItem (no "id" field)
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "description" to cleanDesc,
                "category" to validCategory,
                "priority" to validPriority,
                "timeSlot" to cleanTimeSlot,
                "dateStr" to cleanDateStr,
                "completed" to false,
                "aiGenerated" to aiGenerated,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun toggleScheduleCompleted(
        userId: String,
        scheduleId: String,
        completed: Boolean
    ): Result<Unit> {
        var docPath = "users/$userId/schedules/$scheduleId"
        return try {
            val uid = requireUserId(userId)
            require(scheduleId.isNotBlank()) { "ID jadwal tidak boleh kosong" }
            val docRef = db.collection("users").document(uid).collection("schedules").document(scheduleId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "completed" to completed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateScheduleDetails(
        userId: String,
        scheduleId: String,
        title: String,
        description: String,
        category: String,
        priority: String,
        timeSlot: String
    ): Result<Unit> {
        var docPath = "users/$userId/schedules/$scheduleId"
        return try {
            val uid = requireUserId(userId)
            require(scheduleId.isNotBlank()) { "ID jadwal tidak boleh kosong" }
            val cleanTitle = title.trim().take(150).ifEmpty { "Aktivitas Terjadwal" }
            val cleanDesc = description.trim().take(1000)
            val validCategory = ScheduleCategory.fromRaw(category).name
            val validPriority = SchedulePriority.fromRaw(priority).name
            val cleanTimeSlot = timeSlot.trim().take(40).ifEmpty { "09:00 - 10:00" }

            val docRef = db.collection("users").document(uid).collection("schedules").document(scheduleId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "title" to cleanTitle,
                    "description" to cleanDesc,
                    "category" to validCategory,
                    "priority" to validPriority,
                    "timeSlot" to cleanTimeSlot,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteSchedule(userId: String, scheduleId: String): Result<Unit> {
        var docPath = "users/$userId/schedules/$scheduleId"
        return try {
            val uid = requireUserId(userId)
            require(scheduleId.isNotBlank()) { "ID jadwal tidak boleh kosong" }
            val docRef = db.collection("users").document(uid).collection("schedules").document(scheduleId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    suspend fun addNote(
        userId: String,
        title: String,
        content: String,
        tag: String
    ): Result<Unit> {
        var docPath = "users/$userId/notes"
        return try {
            val uid = requireUserId(userId)
            val cleanTitle = title.trim().take(150).ifEmpty { "Catatan Hidayat AI" }
            val cleanContent = content.trim().take(5000).ifEmpty { "-" }
            val validTag = NoteTag.fromRaw(tag).name

            val docRef = db.collection("users").document(uid).collection("notes").document()
            docPath = docRef.path
            val payload = mapOf(
                "userId" to uid,
                "title" to cleanTitle,
                "content" to cleanContent,
                "tag" to validTag,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            docRef.set(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateNote(
        userId: String,
        noteId: String,
        title: String,
        content: String,
        tag: String
    ): Result<Unit> {
        var docPath = "users/$userId/notes/$noteId"
        return try {
            val uid = requireUserId(userId)
            require(noteId.isNotBlank()) { "ID catatan tidak boleh kosong" }
            val cleanTitle = title.trim().take(150).ifEmpty { "Catatan Hidayat AI" }
            val cleanContent = content.trim().take(5000).ifEmpty { "-" }
            val validTag = NoteTag.fromRaw(tag).name

            val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
            docPath = docRef.path
            docRef.update(
                mapOf(
                    "title" to cleanTitle,
                    "content" to cleanContent,
                    "tag" to validTag,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun deleteNote(userId: String, noteId: String): Result<Unit> {
        var docPath = "users/$userId/notes/$noteId"
        return try {
            val uid = requireUserId(userId)
            require(noteId.isNotBlank()) { "ID catatan tidak boleh kosong" }
            val docRef = db.collection("users").document(uid).collection("notes").document(noteId)
            docPath = docRef.path
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }
}
