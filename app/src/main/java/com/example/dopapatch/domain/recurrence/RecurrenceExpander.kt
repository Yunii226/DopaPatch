package com.example.dopapatch.domain.recurrence

import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.RecurrenceRule
import java.time.LocalDate

/**
 * Expands RFC-5545 RRULEs via lib-recur. Pure Kotlin (no Android) so it unit-tests on the JVM.
 * Do NOT hand-roll recurrence math — lib-recur owns the calendar edge cases.
 */
object RecurrenceExpander {

    /** True if the recurrent task with [rrule] anchored at [dtstart] has an occurrence on [date]. */
    fun occursOn(rrule: String, dtstart: LocalDate, date: LocalDate): Boolean =
        // Bad rules shouldn't crash the checklist that calls this per-render.
        runCatching { occurrences(rrule, dtstart, date, date).isNotEmpty() }.getOrDefault(false)

    /** Occurrence dates in [from]..[to] inclusive. Caps iterations so an unbounded rule can't spin. */
    fun occurrences(rrule: String, dtstart: LocalDate, from: LocalDate, to: LocalDate): List<LocalDate> {
        if (from.isAfter(to)) return emptyList()
        val it = RecurrenceRule(rrule).iterator(dtstart.toAllDay())
        it.fastForward(from.toAllDay())
        val out = ArrayList<LocalDate>()
        var guard = 0
        while (it.hasNext() && guard++ < MAX_ITERATIONS) {
            val d = it.nextDateTime().toLocalDate()
            if (d.isAfter(to)) break
            if (!d.isBefore(from)) out += d
        }
        return out
    }

    private const val MAX_ITERATIONS = 10_000 // ~27 years of a daily rule; loop breaks at `to` first.

    // lib-recur DateTime months are 0-based; these bridge to java.time.
    private fun LocalDate.toAllDay(): DateTime = DateTime(year, monthValue - 1, dayOfMonth)
    private fun DateTime.toLocalDate(): LocalDate = LocalDate.of(year, month + 1, dayOfMonth)
}
