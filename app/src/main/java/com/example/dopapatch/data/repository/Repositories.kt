package com.example.dopapatch.data.repository

import com.example.dopapatch.data.local.DailyNoteDao
import com.example.dopapatch.data.local.DailyNoteEntity
import com.example.dopapatch.data.local.TaskDao
import com.example.dopapatch.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * UI reads from Room (offline-first). Every local write stamps `updatedAt = now` and
 * `dirty = true`; [com.example.dopapatch.data.sync.SyncManager] pushes dirty rows later.
 * `currentUserId` is the signed-in Supabase uid (for RLS on push); null when signed out.
 */
class TaskRepository(
    private val dao: TaskDao,
    private val currentUserId: () -> String?,
) {
    fun observe(): Flow<List<TaskEntity>> = dao.observeActive()

    /** Insert or update. Pass an existing `id` to edit, or blank to create a new row. */
    suspend fun save(task: TaskEntity): TaskEntity {
        val now = Instant.now()
        val row = task.copy(
            id = task.id.ifBlank { UUID.randomUUID().toString() },
            userId = task.userId.ifBlank { currentUserId().orEmpty() },
            updatedAt = now,
            dirty = true,
        )
        dao.upsert(row)
        return row
    }

    suspend fun softDelete(id: String) {
        val t = dao.getById(id) ?: return
        val now = Instant.now()
        dao.upsert(t.copy(deletedAt = now, updatedAt = now, dirty = true))
    }
}

class NoteRepository(
    private val dao: DailyNoteDao,
    private val currentUserId: () -> String?,
) {
    fun observe(date: LocalDate): Flow<DailyNoteEntity?> = dao.observeByDate(date)

    /** Upsert the (single) note for a day, keyed by date. */
    suspend fun saveForDate(date: LocalDate, contentMd: String): DailyNoteEntity {
        val now = Instant.now()
        val existing = dao.getByDate(date)
        val row = (existing?.copy(contentMd = contentMd, updatedAt = now, dirty = true, deletedAt = null))
            ?: DailyNoteEntity(
                id = UUID.randomUUID().toString(),
                userId = currentUserId().orEmpty(),
                noteDate = date,
                contentMd = contentMd,
                updatedAt = now,
                deletedAt = null,
                dirty = true,
            )
        dao.upsert(row)
        return row
    }
}
