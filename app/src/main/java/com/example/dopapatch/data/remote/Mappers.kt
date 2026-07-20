package com.example.dopapatch.data.remote

import com.example.dopapatch.data.local.CompletionEntity
import com.example.dopapatch.data.local.DailyNoteEntity
import com.example.dopapatch.data.local.NoteImageEntity
import com.example.dopapatch.data.local.TaskEntity
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime

// --- ISO string <-> java.time. Postgres timestamptz comes with an offset (…+00:00), which
// Instant.parse rejects, so go through OffsetDateTime. We emit UTC 'Z' form; Postgres accepts it.
private fun parseInstant(s: String): Instant = OffsetDateTime.parse(s).toInstant()
private fun Instant.iso(): String = toString()

fun TaskEntity.toDto(): TaskDto = TaskDto(
    id = id, userId = userId, title = title, description = description, kind = kind,
    scheduledDate = scheduledDate?.toString(), scheduledTime = scheduledTime?.toString(),
    durationMin = durationMin, rrule = rrule, dtstart = dtstart?.toString(),
    alarmEnabled = alarmEnabled, sortOrder = sortOrder,
    createdAt = createdAt.iso(), updatedAt = updatedAt.iso(), deletedAt = deletedAt?.iso(),
)

/** Remote rows are authoritative & already synced, so `dirty = false`. */
fun TaskDto.toEntity(): TaskEntity = TaskEntity(
    id = id, userId = userId, title = title, description = description, kind = kind,
    scheduledDate = scheduledDate?.let(LocalDate::parse),
    scheduledTime = scheduledTime?.let(LocalTime::parse),
    durationMin = durationMin, rrule = rrule, dtstart = dtstart?.let(LocalDate::parse),
    alarmEnabled = alarmEnabled, sortOrder = sortOrder,
    createdAt = parseInstant(createdAt), updatedAt = parseInstant(updatedAt),
    deletedAt = deletedAt?.let(::parseInstant), dirty = false,
)

fun DailyNoteEntity.toDto(): DailyNoteDto = DailyNoteDto(
    id = id, userId = userId, noteDate = noteDate.toString(), contentMd = contentMd,
    updatedAt = updatedAt.iso(), deletedAt = deletedAt?.iso(),
)

fun DailyNoteDto.toEntity(): DailyNoteEntity = DailyNoteEntity(
    id = id, userId = userId, noteDate = LocalDate.parse(noteDate), contentMd = contentMd,
    updatedAt = parseInstant(updatedAt), deletedAt = deletedAt?.let(::parseInstant), dirty = false,
)

fun CompletionEntity.toDto(): CompletionDto = CompletionDto(
    id = id, taskId = taskId, userId = userId,
    occurredOn = occurredOn.toString(), completedAt = completedAt.iso(),
)

fun CompletionDto.toEntity(): CompletionEntity = CompletionEntity(
    id = id, taskId = taskId, userId = userId,
    occurredOn = LocalDate.parse(occurredOn), completedAt = parseInstant(completedAt), dirty = false,
)

fun NoteImageEntity.toDto(): NoteImageDto = NoteImageDto(
    id = id, noteId = noteId, userId = userId, storagePath = storagePath, createdAt = createdAt.iso(),
)

fun NoteImageDto.toEntity(): NoteImageEntity = NoteImageEntity(
    id = id, noteId = noteId, userId = userId, storagePath = storagePath,
    createdAt = parseInstant(createdAt), dirty = false,
)
