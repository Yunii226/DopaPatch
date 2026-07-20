package com.example.dopapatch.ui.checklist

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.dopapatch.data.local.TaskEntity
import com.example.dopapatch.domain.model.KIND_EVENT
import com.example.dopapatch.domain.model.KIND_RECURRENT
import com.example.dopapatch.domain.recurrence.RecurrenceType
import com.example.dopapatch.domain.recurrence.buildRrule
import com.example.dopapatch.domain.recurrence.parseRrule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

private const val MS_PER_DAY = 86_400_000L

/**
 * Add (editing == null) or edit a task. Builds a [TaskEntity] and hands it up via [onSave];
 * the repository stamps id/updatedAt/dirty. Recurrence is edited with the basic builder and
 * emitted as an RFC-5545 RRULE.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskSheet(
    editing: TaskEntity?,
    onDismiss: () -> Unit,
    onSave: (TaskEntity) -> Unit,
) {
    val today = LocalDate.now()
    val seeded = editing?.rrule?.let(::parseRrule)

    var title by remember { mutableStateOf(editing?.title ?: "") }
    var description by remember { mutableStateOf(editing?.description ?: "") }
    var isRecurrent by remember { mutableStateOf(editing?.kind != KIND_EVENT) }
    var date by remember { mutableStateOf(editing?.scheduledDate ?: editing?.dtstart ?: today) }
    var time by remember { mutableStateOf(editing?.scheduledTime) }
    var alarm by remember { mutableStateOf(editing?.alarmEnabled ?: false) }

    var recType by remember { mutableStateOf(seeded?.type ?: RecurrenceType.DAILY) }
    var weekdays by remember { mutableStateOf(seeded?.weekdays ?: emptySet()) }
    var interval by remember { mutableStateOf((seeded?.interval ?: 2).toString()) }
    var until by remember { mutableStateOf(seeded?.until) }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (editing == null) "New task" else "Edit task", style = androidx.compose.material3.MaterialTheme.typography.titleLarge)

            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Title") }, singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("Description (optional)") },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = isRecurrent, onClick = { isRecurrent = true },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                ) { Text("Recurring") }
                SegmentedButton(
                    selected = !isRecurrent, onClick = { isRecurrent = false },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                ) { Text("One-off") }
            }

            DateField(if (isRecurrent) "Starts" else "Date", date) { date = it }
            TimeField(time) { time = it }

            if (isRecurrent) {
                RecurrenceEditor(
                    type = recType, onType = { recType = it },
                    weekdays = weekdays, onToggleDay = { d ->
                        weekdays = if (d in weekdays) weekdays - d else weekdays + d
                    },
                    interval = interval, onInterval = { interval = it.filter(Char::isDigit).take(3) },
                    until = until, onUntil = { until = it }, onClearUntil = { until = null },
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Alarm at time")
                Switch(checked = alarm, onCheckedChange = { alarm = it }, enabled = time != null)
            }

            Button(
                onClick = {
                    val now = Instant.now()
                    val rrule = if (isRecurrent) {
                        buildRrule(recType, weekdays, interval.toIntOrNull() ?: 2, until)
                    } else null
                    onSave(
                        TaskEntity(
                            id = editing?.id ?: "",
                            userId = editing?.userId ?: "",
                            title = title.trim(),
                            description = description.trim().ifBlank { null },
                            kind = if (isRecurrent) KIND_RECURRENT else KIND_EVENT,
                            scheduledDate = if (isRecurrent) null else date,
                            scheduledTime = time,
                            durationMin = editing?.durationMin,
                            rrule = rrule,
                            dtstart = if (isRecurrent) date else null,
                            alarmEnabled = alarm && time != null,
                            sortOrder = editing?.sortOrder ?: 0,
                            createdAt = editing?.createdAt ?: now,
                            updatedAt = now,
                            deletedAt = null,
                        )
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Save") }
        }
    }
}

@Composable
private fun RecurrenceEditor(
    type: RecurrenceType, onType: (RecurrenceType) -> Unit,
    weekdays: Set<DayOfWeek>, onToggleDay: (DayOfWeek) -> Unit,
    interval: String, onInterval: (String) -> Unit,
    until: LocalDate?, onUntil: (LocalDate) -> Unit, onClearUntil: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            RecurrenceType.entries.forEachIndexed { i, t ->
                SegmentedButton(
                    selected = type == t, onClick = { onType(t) },
                    shape = SegmentedButtonDefaults.itemShape(i, RecurrenceType.entries.size),
                ) { Text(when (t) { RecurrenceType.DAILY -> "Daily"; RecurrenceType.WEEKLY -> "Weekly"; RecurrenceType.EVERY_N -> "Every N" }) }
            }
        }
        when (type) {
            RecurrenceType.WEEKLY -> Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DayOfWeek.entries.forEach { d ->
                    FilterChip(
                        selected = d in weekdays, onClick = { onToggleDay(d) },
                        label = { Text(d.name.take(2)) },
                    )
                }
            }
            RecurrenceType.EVERY_N -> OutlinedTextField(
                value = interval, onValueChange = onInterval,
                label = { Text("Every N days") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )
            RecurrenceType.DAILY -> Unit
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Until", until) { onUntil(it) }
            if (until != null) TextButton(onClick = onClearUntil) { Text("No end") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: LocalDate?, onPick: (LocalDate) -> Unit) {
    var show by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { show = true }) { Text("$label: ${date?.toString() ?: "—"}") }
    if (show) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date?.toEpochDay()?.times(MS_PER_DAY))
        DatePickerDialog(
            onDismissRequest = { show = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / MS_PER_DAY)) }
                    show = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimeField(time: LocalTime?, onPick: (LocalTime?) -> Unit) {
    var show by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { show = true }) { Text("Time: ${time?.toString() ?: "None"}") }
        if (time != null) TextButton(onClick = { onPick(null) }) { Text("Clear") }
    }
    if (show) {
        val state = rememberTimePickerState(initialHour = time?.hour ?: 9, initialMinute = time?.minute ?: 0)
        AlertDialog(
            onDismissRequest = { show = false },
            confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); show = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { show = false }) { Text("Cancel") } },
            text = { TimePicker(state = state) },
        )
    }
}
