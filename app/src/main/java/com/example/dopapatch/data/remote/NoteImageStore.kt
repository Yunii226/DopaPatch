package com.example.dopapatch.data.remote

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.storage.storage
import java.util.UUID
import kotlin.time.Duration.Companion.hours

private const val BUCKET = "note-images"

/**
 * Note images live in Supabase Storage; the note's Markdown carries the object path
 * (`![](<uid>/<file>)`), so images travel with the note text and need no extra sync.
 * The path's first segment MUST be the uid — storage RLS keys off it (see supabase/schema.sql).
 *
 * ponytail: no `note_images` bookkeeping table and no orphan GC — deleting an image from the
 * note text leaves the object in the bucket. Add a cleanup pass if the free tier ever fills.
 */
class NoteImageStore(
    private val supabase: SupabaseClient,
    private val currentUserId: () -> String?,
) {
    /** Uploads [bytes] and returns the storage path to embed in the note. */
    suspend fun upload(bytes: ByteArray, ext: String = "jpg"): String {
        val uid = requireNotNull(currentUserId()) { "not signed in" }
        val path = "$uid/${UUID.randomUUID()}.$ext"
        supabase.storage.from(BUCKET).upload(path, bytes) { upsert = false }
        return path
    }

    /** Bucket is private, so rendering needs a short-lived signed URL. */
    suspend fun signedUrl(path: String): String =
        supabase.storage.from(BUCKET).createSignedUrl(path, 1.hours)
}
