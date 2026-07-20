package com.example.dopapatch.data.sync

import android.content.Context
import com.example.dopapatch.data.local.DopaPatchDb
import com.example.dopapatch.data.remote.DailyNoteDto
import com.example.dopapatch.data.remote.TaskDto
import com.example.dopapatch.data.remote.toDto
import com.example.dopapatch.data.remote.toEntity
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import java.time.Instant

/** Persisted sync cursor. One `lastSyncAt` for all tables is fine for a single-user app. */
class SyncPrefs(context: Context) {
    private val sp = context.getSharedPreferences("dopapatch.sync", Context.MODE_PRIVATE)
    fun lastSyncAt(): Instant = Instant.ofEpochMilli(sp.getLong(KEY, 0L))
    fun setLastSyncAt(v: Instant) = sp.edit().putLong(KEY, v.toEpochMilli()).apply()
    private companion object { const val KEY = "last_sync_at" }
}

/**
 * Offline-first two-way sync for the LWW-soft-delete tables (tasks, daily_notes):
 * push dirty rows up, pull rows changed since the cursor, resolve by [shouldApplyRemote].
 * ponytail: completions + note_images sync deferred to Phase 4/5 & 8 when those features
 * write them; ponytail: single client-clock cursor (skew risk) — overlap re-pulls are
 * idempotent, upgrade to a server-time cursor only if drift bites.
 */
class SyncManager(
    private val supabase: SupabaseClient,
    private val db: DopaPatchDb,
    private val prefs: SyncPrefs,
    private val currentUserId: () -> String?,
) {
    suspend fun sync(): Result<Unit> = runCatching {
        val uid = currentUserId() ?: return@runCatching // signed out — nothing to sync
        val start = Instant.now()
        val since = prefs.lastSyncAt().toString()

        pushTasks(uid); pushNotes(uid)
        pullTasks(since); pullNotes(since)

        prefs.setLastSyncAt(start)
    }

    private suspend fun pushTasks(uid: String) {
        val dirty = db.taskDao().getDirty()
        if (dirty.isEmpty()) return
        supabase.from("tasks").upsert(dirty.map { it.copy(userId = uid).toDto() })
        db.taskDao().upsertAll(dirty.map { it.copy(userId = uid, dirty = false) })
    }

    private suspend fun pullTasks(since: String) {
        val remotes = supabase.from("tasks")
            .select { filter { gt("updated_at", since) } }
            .decodeList<TaskDto>()
        val dao = db.taskDao()
        val apply = remotes.map { it.toEntity() }
            .filter { shouldApplyRemote(dao.getById(it.id)?.updatedAt, it.updatedAt) }
        if (apply.isNotEmpty()) dao.upsertAll(apply)
    }

    private suspend fun pushNotes(uid: String) {
        val dirty = db.dailyNoteDao().getDirty()
        if (dirty.isEmpty()) return
        supabase.from("daily_notes").upsert(dirty.map { it.copy(userId = uid).toDto() })
        db.dailyNoteDao().upsertAll(dirty.map { it.copy(userId = uid, dirty = false) })
    }

    private suspend fun pullNotes(since: String) {
        val remotes = supabase.from("daily_notes")
            .select { filter { gt("updated_at", since) } }
            .decodeList<DailyNoteDto>()
        val dao = db.dailyNoteDao()
        val apply = remotes.map { it.toEntity() }
            .filter { shouldApplyRemote(dao.getById(it.id)?.updatedAt, it.updatedAt) }
        if (apply.isNotEmpty()) dao.upsertAll(apply)
    }
}
