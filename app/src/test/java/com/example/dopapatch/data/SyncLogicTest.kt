package com.example.dopapatch.data

import com.example.dopapatch.data.local.DailyNoteEntity
import com.example.dopapatch.data.local.TaskEntity
import com.example.dopapatch.data.remote.toDto
import com.example.dopapatch.data.remote.toEntity
import com.example.dopapatch.data.sync.shouldApplyRemote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class SyncLogicTest {

    @Test fun task_dto_roundtrip_preserves_all_fields() {
        val e = TaskEntity(
            id = "t1", userId = "u1", title = "Meditate", description = null, kind = "recurrent",
            scheduledDate = null, scheduledTime = LocalTime.of(9, 0), durationMin = 15,
            rrule = "FREQ=DAILY", dtstart = LocalDate.of(2026, 7, 20),
            alarmEnabled = true, sortOrder = 3,
            createdAt = Instant.parse("2026-07-20T08:00:00Z"),
            updatedAt = Instant.parse("2026-07-20T09:30:00Z"),
            deletedAt = null, dirty = true,
        )
        // dirty is local-only; a round trip through the wire comes back synced (dirty=false).
        assertEquals(e.copy(dirty = false), e.toDto().toEntity())
    }

    @Test fun note_dto_roundtrip_with_softdelete() {
        val e = DailyNoteEntity(
            id = "n1", userId = "u1", noteDate = LocalDate.of(2026, 7, 20),
            contentMd = "# hi", updatedAt = Instant.parse("2026-07-20T10:00:00Z"),
            deletedAt = Instant.parse("2026-07-20T11:00:00Z"), dirty = true,
        )
        assertEquals(e.copy(dirty = false), e.toDto().toEntity())
    }

    @Test fun lww_remote_wins_only_when_strictly_newer() {
        val older = Instant.parse("2026-07-20T10:00:00Z")
        val newer = Instant.parse("2026-07-20T10:00:01Z")
        assertTrue("newer remote overwrites", shouldApplyRemote(older, newer))
        assertFalse("older remote is ignored", shouldApplyRemote(newer, older))
        assertFalse("tie keeps local (dirty gets pushed)", shouldApplyRemote(older, older))
        assertTrue("no local row => take remote", shouldApplyRemote(null, older))
    }
}
