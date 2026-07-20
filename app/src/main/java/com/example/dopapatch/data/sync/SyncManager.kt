package com.example.dopapatch.data.sync

import android.content.Context
import com.example.dopapatch.data.local.DopaPatchDb
import com.example.dopapatch.data.remote.CompletionDto
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
 * Offline-first two-way sync. Tasks + daily_notes use LWW soft-delete (push dirty, pull changed
 * since cursor, resolve by [shouldApplyRemote]). Completions have no server soft-delete, so they
 * sync as insert/delete via a local `deleted` tombstone.
 * ponytail: note_images sync deferred to Phase 8; single client-clock cursor (skew risk) —
 * overlap re-pulls are idempotent, upgrade to a server-time cursor only if drift bites.
 * ponytail: cross-device offline un-check of a completion won't propagate (no server tombstone) —
 * fine for a 1-2 device personal app; revisit if it bites.
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

        pushTasks(uid); pushNotes(uid); pushCompletions(uid)
        pullTasks(since); pullNotes(since); pullCompletions(since)

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

    private suspend fun pushCompletions(uid: String) {
        val dao = db.completionDao()
        for (c in dao.getDirty()) {
            if (c.deleted) {
                supabase.from("task_completions").delete { filter { eq("id", c.id) } }
                dao.deleteById(c.id) // tombstone pushed — drop it locally
            } else {
                supabase.from("task_completions").upsert(c.copy(userId = uid).toDto())
                dao.upsert(c.copy(userId = uid, dirty = false))
            }
        }
    }

    private suspend fun pullCompletions(since: String) {
        val remotes = supabase.from("task_completions")
            .select { filter { gt("completed_at", since) } }
            .decodeList<CompletionDto>()
        val dao = db.completionDao()
        for (dto in remotes) {
            val e = dto.toEntity()
            val local = dao.get(e.taskId, e.occurredOn)
            when {
                local == null -> dao.upsert(e)
                local.dirty -> Unit // local edit wins; it'll be pushed next round
                local.id != e.id -> { dao.deleteById(local.id); dao.upsert(e) } // reconcile id clash on unique(task,day)
                else -> dao.upsert(e)
            }
        }
    }
}
