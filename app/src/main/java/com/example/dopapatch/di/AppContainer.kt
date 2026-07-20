package com.example.dopapatch.di

import android.content.Context
import com.example.dopapatch.BuildConfig
import com.example.dopapatch.data.alarm.AlarmScheduler
import com.example.dopapatch.data.local.DopaPatchDb
import com.example.dopapatch.data.repository.CompletionRepository
import com.example.dopapatch.data.repository.NoteRepository
import com.example.dopapatch.data.repository.TaskRepository
import com.example.dopapatch.data.sync.SyncManager
import com.example.dopapatch.data.sync.SyncPrefs
import com.example.dopapatch.domain.usecase.GetTasksForDate
import com.example.dopapatch.domain.usecase.ToggleCompletion
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest

/** Config sourced from BuildConfig (populated from local.properties). */
data class AppConfig(
    val supabaseUrl: String,
    val supabaseAnonKey: String,
    val geminiApiKey: String,
) {
    val isBackendConfigured: Boolean get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
}

/**
 * Manual DI container — app-wide singletons live here, created once in [DopaPatchApp].
 * Hilt's Gradle plugin is incompatible with AGP 9 (google/dagger#4944), and a single-module
 * personal app doesn't need a DI framework.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val config = AppConfig(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
        geminiApiKey = BuildConfig.GEMINI_API_KEY,
    )

    // Built lazily so a fresh clone with empty keys still launches (createSupabaseClient
    // rejects blank url/key). Only touched behind config.isBackendConfigured.
    val supabase by lazy {
        createSupabaseClient(config.supabaseUrl, config.supabaseAnonKey) {
            install(Auth) // session auto-persists to SharedPreferences + auto-refreshes.
            install(Postgrest)
        }
    }

    val auth get() = supabase.auth
    fun currentUserId(): String? = auth.currentUserOrNull()?.id

    private val db by lazy { DopaPatchDb.build(appContext) }

    val taskRepository by lazy { TaskRepository(db.taskDao(), ::currentUserId) }
    val noteRepository by lazy { NoteRepository(db.dailyNoteDao(), ::currentUserId) }
    val completionRepository by lazy { CompletionRepository(db.completionDao(), ::currentUserId) }

    val getTasksForDate by lazy { GetTasksForDate(taskRepository, completionRepository) }
    val toggleCompletion by lazy { ToggleCompletion(completionRepository) }

    val syncManager by lazy {
        SyncManager(supabase, db, SyncPrefs(appContext), ::currentUserId)
    }

    val alarmScheduler by lazy { AlarmScheduler(appContext) }

    /** (Re)schedule every alarm-enabled task — after edits, sync, or reboot. */
    suspend fun rescheduleAlarms() = alarmScheduler.scheduleAll(db.taskDao().alarmTasks())

    /** Schedule the next occurrence of one task (used after an alarm fires). */
    suspend fun rescheduleTask(taskId: String) {
        db.taskDao().getById(taskId)?.let { alarmScheduler.schedule(it) }
    }
}
