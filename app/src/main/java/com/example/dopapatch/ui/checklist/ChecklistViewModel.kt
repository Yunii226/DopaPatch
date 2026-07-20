package com.example.dopapatch.ui.checklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.dopapatch.DopaPatchApp
import com.example.dopapatch.data.local.TaskEntity
import com.example.dopapatch.data.repository.TaskRepository
import com.example.dopapatch.data.sync.SyncManager
import com.example.dopapatch.domain.model.DayTask
import com.example.dopapatch.domain.model.KIND_EVENT
import com.example.dopapatch.domain.model.KIND_RECURRENT
import com.example.dopapatch.domain.usecase.GetTasksForDate
import com.example.dopapatch.domain.usecase.ToggleCompletion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ChecklistUiState(
    val date: LocalDate = LocalDate.now(),
    val recurrent: List<DayTask> = emptyList(),
    val events: List<DayTask> = emptyList(),
    val doneCount: Int = 0,
    val totalCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
class ChecklistViewModel(
    private val getTasksForDate: GetTasksForDate,
    private val toggleCompletion: ToggleCompletion,
    private val tasks: TaskRepository,
    private val sync: SyncManager,
) : ViewModel() {

    // Phase 5 is "today"; a date selector arrives in Phase 6.
    private val date = MutableStateFlow(LocalDate.now())

    val uiState = date
        .flatMapLatest { d -> getTasksForDate(d).map { list -> toUi(d, list) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ChecklistUiState())

    private fun toUi(d: LocalDate, list: List<DayTask>) = ChecklistUiState(
        date = d,
        recurrent = list.filter { it.task.kind == KIND_RECURRENT },
        events = list.filter { it.task.kind == KIND_EVENT },
        doneCount = list.count { it.done },
        totalCount = list.size,
    )

    fun toggle(taskId: String) = viewModelScope.launch {
        toggleCompletion(taskId, date.value); syncQuietly()
    }

    fun delete(taskId: String) = viewModelScope.launch {
        tasks.softDelete(taskId); syncQuietly()
    }

    fun save(task: TaskEntity) = viewModelScope.launch {
        tasks.save(task); syncQuietly()
    }

    /** Fire-and-forget push/pull; SyncManager swallows offline errors. */
    private fun syncQuietly() = viewModelScope.launch(Dispatchers.IO) { sync.sync() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val c = (this[APPLICATION_KEY] as DopaPatchApp).container
                ChecklistViewModel(c.getTasksForDate, c.toggleCompletion, c.taskRepository, c.syncManager)
            }
        }
    }
}
