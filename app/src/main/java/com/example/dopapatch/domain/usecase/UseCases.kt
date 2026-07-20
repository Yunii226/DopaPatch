package com.example.dopapatch.domain.usecase

import com.example.dopapatch.data.repository.CompletionRepository
import com.example.dopapatch.data.repository.TaskRepository
import com.example.dopapatch.domain.model.DayTask
import com.example.dopapatch.domain.model.buildDayTasks
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate

/** Today's tasks (recurrent occurrences + events) joined with per-day completion. */
class GetTasksForDate(
    private val tasks: TaskRepository,
    private val completions: CompletionRepository,
) {
    operator fun invoke(date: LocalDate): Flow<List<DayTask>> =
        combine(tasks.observe(), completions.observeDoneIds(date)) { taskList, doneIds ->
            buildDayTasks(taskList, doneIds, date)
        }
}

/** Check/un-check a task for a single day; other days are untouched. */
class ToggleCompletion(private val completions: CompletionRepository) {
    suspend operator fun invoke(taskId: String, date: LocalDate) = completions.toggle(taskId, date)
}
