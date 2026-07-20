package com.example.dopapatch.ui.checklist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dopapatch.data.local.TaskEntity
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val DATE_FMT = DateTimeFormatter.ofPattern("EEE, MMM d")

/** Top-level day screen: switch between Checklist and Time-blocks for the selected date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayScreen(
    onSignOut: () -> Unit,
    vm: DayViewModel = viewModel(factory = DayViewModel.Factory),
) {
    val ui by vm.uiState.collectAsState()

    // Ask for notification permission once (Android 13+); alarms are useless without it.
    val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    var tab by remember { mutableStateOf(0) } // 0 = Checklist, 1 = Time-blocks
    var sheetOpen by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<TaskEntity?>(null) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("${ui.doneCount}/${ui.totalCount} done") },
                actions = { TextButton(onClick = onSignOut) { Text("Sign out") } },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { editing = null; sheetOpen = true }) {
                Text("+", style = MaterialTheme.typography.headlineSmall)
            }
        },
    ) { pad ->
        Column(Modifier.fillMaxSize().padding(pad)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Checklist") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Time-blocks") })
            }
            DateBar(ui.date, onPrev = vm::prevDay, onNext = vm::nextDay, onToday = vm::goToday)

            val onEdit: (TaskEntity) -> Unit = { editing = it; sheetOpen = true }
            when (tab) {
                0 -> ChecklistBody(ui, vm::toggle, vm::delete, onEdit)
                else -> TimeBlocksBody(ui, vm::toggle, onEdit)
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

@Composable
private fun DateBar(date: LocalDate, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        TextButton(onClick = onPrev) { Text("‹") }
        val label = if (date == LocalDate.now()) "Today" else date.format(DATE_FMT)
        TextButton(onClick = onToday) { Text(label) }
        TextButton(onClick = onNext) { Text("›") }
    }
}
