package com.example.dopapatch.domain.model

import com.example.dopapatch.data.local.TaskEntity
import com.example.dopapatch.domain.recurrence.RecurrenceExpander
import com.example.dopapatch.domain.timeblock.TimeBlock
import com.example.dopapatch.domain.timeblock.TimeBlocks
import java.time.LocalDate

const val KIND_RECURRENT = "recurrent"
const val KIND_EVENT = "event"

/** A task as it appears on a given day: the row + whether it's done that day + its time-block. */
data class DayTask(
    val task: TaskEntity,
    val done: Boolean,
    val timeBlock: TimeBlock,
)

/** Does [task] appear on [date]? Events show only on their date; recurrent tasks via the RRULE. */
fun activeOn(task: TaskEntity, date: LocalDate): Boolean = when (task.kind) {
    KIND_EVENT -> task.scheduledDate == date
    KIND_RECURRENT -> task.rrule != null && task.dtstart != null &&
        RecurrenceExpander.occursOn(task.rrule, task.dtstart, date)
    else -> false
}

/**
 * Pure join: today's active tasks tagged with completion + time-block. [doneTaskIds] is the set
 * of task ids completed *on [date]* — completion is per-occurrence-per-day, so a different day
 * passes a different set and the same task can be done on one day and not another.
 */
fun buildDayTasks(tasks: List<TaskEntity>, doneTaskIds: Set<String>, date: LocalDate): List<DayTask> =
    tasks.filter { activeOn(it, date) }
        .map { DayTask(it, done = it.id in doneTaskIds, timeBlock = TimeBlocks.of(it.scheduledTime)) }
