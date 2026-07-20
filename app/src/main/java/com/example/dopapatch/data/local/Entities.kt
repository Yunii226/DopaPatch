package com.example.dopapatch.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

// Sync bookkeeping shared by the LWW-soft-delete tables: `dirty` = has unpushed local
// changes; `updatedAt` = last-write-wins clock; `deletedAt` = soft-delete tombstone.

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val title: String,
    val description: String?,
    val kind: String,                 // 'recurrent' | 'event'
    val scheduledDate: LocalDate?,    // events
    val scheduledTime: LocalTime?,
    val durationMin: Int?,
    val rrule: String?,               // recurrent
    val dtstart: LocalDate?,
    val alarmEnabled: Boolean,
    val sortOrder: Int,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val dirty: Boolean = false,
)

@Entity(
    tableName = "task_completions",
    indices = [Index(value = ["taskId", "occurredOn"], unique = true)],
)
data class CompletionEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val userId: String,
    val occurredOn: LocalDate,
    val completedAt: Instant,
    val dirty: Boolean = false,
    // Server task_completions has no soft-delete; `deleted` is a local tombstone so an
    // un-check made offline can be pushed as a DELETE. ponytail: completion sync itself
    // lands in Phase 4/5 (ToggleCompletion) — entity is here so the model is complete.
    val deleted: Boolean = false,
)

@Entity(
    tableName = "daily_notes",
    indices = [Index(value = ["noteDate"], unique = true)],
)
data class DailyNoteEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val noteDate: LocalDate,
    val contentMd: String,
    val updatedAt: Instant,
    val deletedAt: Instant?,
    val dirty: Boolean = false,
)

@Entity(tableName = "note_images")
data class NoteImageEntity(
    @PrimaryKey val id: String,
    val noteId: String,
    val userId: String,
    val storagePath: String,
    val createdAt: Instant,
    val dirty: Boolean = false,
)
