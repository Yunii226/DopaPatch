package com.example.dopapatch.ui.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dopapatch.data.local.TaskEntity
import com.example.dopapatch.domain.model.DayTask

/** Two-section (Recurrent / Added) checkable list with swipe-to-delete. */
@Composable
fun ChecklistBody(
    ui: DayUiState,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ui.totalCount == 0) {
        EmptyDay(modifier)
        return
    }
    LazyColumn(modifier.fillMaxSize()) {
        section("Recurrent", ui.recurrent, onToggle, onDelete, onEdit)
        section("Added", ui.events, onToggle, onDelete, onEdit)
    }
}

@Composable
fun EmptyDay(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), Alignment.Center) {
        Text("Nothing here. Tap + to add a habit or event.")
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text, style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

private fun LazyListScope.section(
    header: String,
    items: List<DayTask>,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (TaskEntity) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "header-$header") { SectionHeader(header) }
    items(items, key = { it.task.id }) { dt ->
        SwipeableRow(dt, onToggle = { onToggle(dt.task.id) }, onDelete = { onDelete(dt.task.id) }, onEdit = { onEdit(dt.task) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableRow(dt: DayTask, onToggle: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
    // ponytail: confirmValueChange is deprecated but still the simplest one-shot swipe-to-delete.
    val dismiss = rememberSwipeToDismissBoxState(
        confirmValueChange = { if (it == SwipeToDismissBoxValue.EndToStart) { onDelete(); true } else false },
    )
    SwipeToDismissBox(
        state = dismiss,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) { Text("Delete", color = MaterialTheme.colorScheme.onErrorContainer) }
        },
    ) {
        TaskRowContent(dt, onToggle = onToggle, onEdit = onEdit, modifier = Modifier.background(MaterialTheme.colorScheme.surface))
    }
}
