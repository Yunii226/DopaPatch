package com.example.dopapatch.ui.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dopapatch.data.local.TaskEntity
import com.example.dopapatch.domain.model.DayTask

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChecklistScreen(
    onSignOut: () -> Unit,
    vm: ChecklistViewModel = viewModel(factory = ChecklistViewModel.Factory),
) {
    val ui by vm.uiState.collectAsState()
    // null = closed; a TaskEntity (possibly blank id) = the sheet target.
    var editing by remember { mutableStateOf<TaskEntity?>(null) }
    var sheetOpen by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Today — ${ui.doneCount}/${ui.totalCount} done") },
                actions = { TextButton(onClick = onSignOut) { Text("Sign out") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; sheetOpen = true }) { Text("+", style = MaterialTheme.typography.headlineSmall) }
        },
    ) { pad ->
        if (ui.totalCount == 0) {
            Box(Modifier.fillMaxSize().padding(pad), Alignment.Center) {
                Text("Nothing for today. Tap + to add a habit or event.")
            }
        } else {
            LazyColumn(Modifier.fillMaxSize().padding(pad)) {
                section("Recurrent", ui.recurrent, vm::toggle, vm::delete) { editing = it; sheetOpen = true }
                section("Added", ui.events, vm::toggle, vm::delete) { editing = it; sheetOpen = true }
            }
        }
    }

    if (sheetOpen) {
        AddEditTaskSheet(
            editing = editing,
            onDismiss = { sheetOpen = false },
            onSave = { vm.save(it); sheetOpen = false },
        )
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    header: String,
    items: List<DayTask>,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit,
    onEdit: (TaskEntity) -> Unit,
) {
    if (items.isEmpty()) return
    item(key = "header-$header") {
        Text(
            header, style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
    items(items, key = { it.task.id }) { dt ->
        TaskRow(dt, onToggle = { onToggle(dt.task.id) }, onDelete = { onDelete(dt.task.id) }, onEdit = { onEdit(dt.task) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskRow(dt: DayTask, onToggle: () -> Unit, onDelete: () -> Unit, onEdit: () -> Unit) {
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
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable(onClick = onEdit)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = dt.done, onCheckedChange = { onToggle() })
            Column(Modifier.padding(start = 4.dp)) {
                Text(
                    dt.task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    textDecoration = if (dt.done) TextDecoration.LineThrough else null,
                    color = if (dt.done) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
                )
                dt.task.scheduledTime?.let {
                    Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
