package com.example.dopapatch.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Wire shapes mirroring the Postgres columns (snake_case). Timestamps/dates are ISO strings;
// mappers convert to java.time. Defaults let Postgres-side defaults fill absent fields.

@Serializable
data class TaskDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    val title: String,
    val description: String? = null,
    val kind: String,
    @SerialName("scheduled_date") val scheduledDate: String? = null,
    @SerialName("scheduled_time") val scheduledTime: String? = null,
    @SerialName("duration_min") val durationMin: Int? = null,
    val rrule: String? = null,
    val dtstart: String? = null,
    @SerialName("alarm_enabled") val alarmEnabled: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class DailyNoteDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("note_date") val noteDate: String,
    @SerialName("content_md") val contentMd: String = "",
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
)

@Serializable
data class CompletionDto(
    val id: String,
    @SerialName("task_id") val taskId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("occurred_on") val occurredOn: String,
    @SerialName("completed_at") val completedAt: String,
)

@Serializable
data class NoteImageDto(
    val id: String,
    @SerialName("note_id") val noteId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("storage_path") val storagePath: String,
    @SerialName("created_at") val createdAt: String,
)
