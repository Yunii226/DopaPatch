package com.example.dopapatch.domain

import com.example.dopapatch.data.local.TaskEntity
import com.example.dopapatch.domain.model.KIND_EVENT
import com.example.dopapatch.domain.model.KIND_RECURRENT
import com.example.dopapatch.domain.model.buildDayTasks
import com.example.dopapatch.domain.recurrence.RecurrenceExpander
import com.example.dopapatch.domain.timeblock.TimeBlock
import com.example.dopapatch.domain.timeblock.TimeBlocks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

class DomainTest {
    private val start = LocalDate.of(2026, 7, 20) // a Monday

    private fun task(
        id: String, kind: String, rrule: String? = null, dtstart: LocalDate? = null,
        scheduledDate: LocalDate? = null, time: LocalTime? = null,
    ) = TaskEntity(
        id = id, userId = "u", title = id, description = null, kind = kind,
        scheduledDate = scheduledDate, scheduledTime = time, durationMin = null,
        rrule = rrule, dtstart = dtstart, alarmEnabled = false, sortOrder = 0,
        createdAt = Instant.EPOCH, updatedAt = Instant.EPOCH, deletedAt = null,
    )

    // ---------- recurrence ----------
    @Test fun daily_occurs_every_day() {
        assertTrue(RecurrenceExpander.occursOn("FREQ=DAILY", start, start))
        assertTrue(RecurrenceExpander.occursOn("FREQ=DAILY", start, start.plusDays(3)))
        assertFalse("no occurrence before dtstart", RecurrenceExpander.occursOn("FREQ=DAILY", start, start.minusDays(1)))
    }

    @Test fun weekly_by_weekday_only_on_those_days() {
        val rule = "FREQ=WEEKLY;BYDAY=MO,WE"
        assertTrue(RecurrenceExpander.occursOn(rule, start, start))               // Mon
        assertTrue(RecurrenceExpander.occursOn(rule, start, start.plusDays(2)))   // Wed
        assertFalse(RecurrenceExpander.occursOn(rule, start, start.plusDays(1)))  // Tue
    }

    @Test fun interval_every_two_days() {
        val rule = "FREQ=DAILY;INTERVAL=2"
        assertTrue(RecurrenceExpander.occursOn(rule, start, start))
        assertFalse(RecurrenceExpander.occursOn(rule, start, start.plusDays(1)))
        assertTrue(RecurrenceExpander.occursOn(rule, start, start.plusDays(2)))
    }

    @Test fun until_stops_after_end_date() {
        val rule = "FREQ=DAILY;UNTIL=20260722" // through Jul 22
        assertTrue(RecurrenceExpander.occursOn(rule, start, start.plusDays(2)))   // Jul 22
        assertFalse(RecurrenceExpander.occursOn(rule, start, start.plusDays(3)))  // Jul 23
    }

    // ---------- events ----------
    @Test fun event_shows_only_on_its_date() {
        val e = task("e1", KIND_EVENT, scheduledDate = start)
        assertEquals(1, buildDayTasks(listOf(e), emptySet(), start).size)
        assertEquals(0, buildDayTasks(listOf(e), emptySet(), start.plusDays(1)).size)
    }

    // ---------- completion is per-day ----------
    @Test fun completing_one_day_does_not_affect_another() {
        val t = task("t1", KIND_RECURRENT, rrule = "FREQ=DAILY", dtstart = start)
        val monday = buildDayTasks(listOf(t), doneTaskIds = setOf("t1"), date = start)
        val tuesday = buildDayTasks(listOf(t), doneTaskIds = emptySet(), date = start.plusDays(1))
        assertTrue("done Monday", monday.single().done)
        assertFalse("not done Tuesday", tuesday.single().done)
    }

    // ---------- time-blocks ----------
    @Test fun time_blocks_map_to_boundaries() {
        assertEquals(TimeBlock.ANYTIME, TimeBlocks.of(null))
        assertEquals(TimeBlock.MORNING, TimeBlocks.of(LocalTime.of(9, 0)))
        assertEquals(TimeBlock.AFTERNOON, TimeBlocks.of(LocalTime.of(12, 0)))
        assertEquals(TimeBlock.EVENING, TimeBlocks.of(LocalTime.of(17, 0)))
        assertEquals(TimeBlock.NIGHT, TimeBlocks.of(LocalTime.of(21, 0)))
        assertEquals(TimeBlock.NIGHT, TimeBlocks.of(LocalTime.of(2, 0)))
    }
}
