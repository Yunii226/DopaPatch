package com.example.dopapatch.domain.alarm

import com.example.dopapatch.data.local.TaskEntity
import com.example.dopapatch.domain.model.KIND_EVENT
import com.example.dopapatch.domain.model.KIND_RECURRENT
import com.example.dopapatch.domain.recurrence.RecurrenceExpander
import java.time.LocalDateTime

/**
 * The next moment [task]'s alarm should fire strictly after [from], or null if none.
 * Events fire once (their date+time); recurrent tasks use the RRULE. Pure — unit-testable.
 */
fun nextOccurrence(task: TaskEntity, from: LocalDateTime): LocalDateTime? {
    val time = task.scheduledTime ?: return null
    return when (task.kind) {
        KIND_EVENT -> task.scheduledDate
            ?.let { LocalDateTime.of(it, time) }
            ?.takeIf { it.isAfter(from) }

        KIND_RECURRENT -> {
            val rrule = task.rrule ?: return null
            val dtstart = task.dtstart ?: return null
            val fromDate = from.toLocalDate()
            RecurrenceExpander.occurrences(rrule, dtstart, fromDate, fromDate.plusDays(HORIZON_DAYS))
                .asSequence()
                .map { LocalDateTime.of(it, time) }
                .firstOrNull { it.isAfter(from) }
        }

        else -> null
    }
}

private const val HORIZON_DAYS = 366L // far enough to find the next fire for any sane rule
