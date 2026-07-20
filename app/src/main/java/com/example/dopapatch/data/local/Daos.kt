package com.example.dopapatch.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL ORDER BY sortOrder, createdAt")
    fun observeActive(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT * FROM tasks WHERE dirty = 1")
    suspend fun getDirty(): List<TaskEntity>

    /** Alarm-eligible tasks (has a time, alarm on, not deleted) — for (re)scheduling. */
    @Query("SELECT * FROM tasks WHERE deletedAt IS NULL AND alarmEnabled = 1 AND scheduledTime IS NOT NULL")
    suspend fun alarmTasks(): List<TaskEntity>

    @Upsert suspend fun upsert(task: TaskEntity)
    @Upsert suspend fun upsertAll(tasks: List<TaskEntity>)
}

@Dao
interface DailyNoteDao {
    @Query("SELECT * FROM daily_notes WHERE noteDate = :date AND deletedAt IS NULL")
    fun observeByDate(date: LocalDate): Flow<DailyNoteEntity?>

    @Query("SELECT * FROM daily_notes WHERE id = :id")
    suspend fun getById(id: String): DailyNoteEntity?

    @Query("SELECT * FROM daily_notes WHERE noteDate = :date")
    suspend fun getByDate(date: LocalDate): DailyNoteEntity?

    @Query("SELECT * FROM daily_notes WHERE dirty = 1")
    suspend fun getDirty(): List<DailyNoteEntity>

    @Upsert suspend fun upsert(note: DailyNoteEntity)
    @Upsert suspend fun upsertAll(notes: List<DailyNoteEntity>)
}

@Dao
interface CompletionDao {
    @Query("SELECT * FROM task_completions WHERE occurredOn = :date AND deleted = 0")
    fun observeByDate(date: LocalDate): Flow<List<CompletionEntity>>

    @Query("SELECT * FROM task_completions WHERE taskId = :taskId AND occurredOn = :date")
    suspend fun get(taskId: String, date: LocalDate): CompletionEntity?

    @Query("SELECT * FROM task_completions WHERE dirty = 1")
    suspend fun getDirty(): List<CompletionEntity>

    @Upsert suspend fun upsert(c: CompletionEntity)
    @Query("DELETE FROM task_completions WHERE id = :id") suspend fun deleteById(id: String)
}

@Dao
interface NoteImageDao {
    @Query("SELECT * FROM note_images WHERE noteId = :noteId")
    suspend fun forNote(noteId: String): List<NoteImageEntity>

    @Upsert suspend fun upsert(img: NoteImageEntity)
    @Query("DELETE FROM note_images WHERE id = :id") suspend fun deleteById(id: String)
}
