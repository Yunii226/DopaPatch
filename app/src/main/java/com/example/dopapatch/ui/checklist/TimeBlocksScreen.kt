package com.example.dopapatch.ui.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.dopapatch.data.local.TaskEntity
import com.example.dopapatch.domain.model.DayTask
import java.time.LocalDate
import java.time.LocalTime

private val HOUR_HEIGHT = 56.dp
private val LABEL_WIDTH = 48.dp
private val MIN_BLOCK = 46.dp
private const val DEFAULT_DURATION_MIN = 60
private const val HOURS = 24

/** Google-Calendar-style day view: all-day strip on top, then a scrollable hour grid with
 *  timed tasks placed at their start (height ∝ duration) and a "now" line when viewing today.
 *  ponytail: overlapping tasks stack (no column-splitting) — fine for a personal habit app. */
@Composable
fun TimeBlocksBody(
    ui: DayUiState,
    onToggle: (String) -> Unit,
    onEdit: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val untimed = ui.all.filter { it.task.scheduledTime == null }
    val timed = ui.all.filter { it.task.scheduledTime != null }
    if (ui.totalCount == 0) {
        EmptyDay(modifier)
        return
    }

    Column(modifier.fillMaxSize()) {
        if (untimed.isNotEmpty()) {
            AllDayStrip(untimed, onToggle, onEdit)
            HorizontalDivider()
        }
        Timeline(timed, isToday = ui.date == LocalDate.now(), onToggle = onToggle, onEdit = onEdit, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun AllDayStrip(tasks: List<DayTask>, onToggle: (String) -> Unit, onEdit: (TaskEntity) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("All-day", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        tasks.forEach { dt ->
            FilterChip(
                selected = dt.done,
                onClick = { onToggle(dt.task.id) },
                label = {
                    Text(dt.task.title, textDecoration = if (dt.done) TextDecoration.LineThrough else null)
                },
            )
        }
    }
}

@Composable
private fun Timeline(
    timed: List<DayTask>,
    isToday: Boolean,
    onToggle: (String) -> Unit,
    onEdit: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    // Land near "now" (or 7am) on open instead of at midnight.
    LaunchedEffect(isToday) {
        val hour = if (isToday) (LocalTime.now().hour - 1).coerceAtLeast(0) else 7
        scroll.scrollTo(with(density) { (HOUR_HEIGHT * hour).toPx() }.toInt())
    }

    Box(modifier.verticalScroll(scroll)) {
        Box(Modifier.fillMaxWidth().height(HOUR_HEIGHT * HOURS)) {
            HourGrid()
            timed.forEach { dt -> TimedBlock(dt, onToggle = { onToggle(dt.task.id) }, onEdit = { onEdit(dt.task) }) }
            if (isToday) NowLine()
        }
    }
}

@Composable
private fun HourGrid() {
    Column(Modifier.fillMaxWidth()) {
        for (h in 0 until HOURS) {
            Row(Modifier.fillMaxWidth().height(HOUR_HEIGHT), verticalAlignment = Alignment.Top) {
                Text(
                    "%02d:00".format(h),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(LABEL_WIDTH).padding(start = 4.dp),
                )
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    HorizontalDivider(Modifier.align(Alignment.TopStart), color = MaterialTheme.colorScheme.outlineVariant)
                }
            }
        }
    }
}

@Composable
private fun TimedBlock(dt: DayTask, onToggle: () -> Unit, onEdit: () -> Unit) {
    val t = dt.task.scheduledTime ?: return
    val startMin = t.hour * 60 + t.minute
    val top: Dp = HOUR_HEIGHT * (startMin / 60f)
    val dur = (dt.task.durationMin ?: DEFAULT_DURATION_MIN).coerceAtLeast(15)
    val h = maxOf(MIN_BLOCK, HOUR_HEIGHT * (dur / 60f))

    // Tap the block to edit; the Checkbox consumes its own taps to toggle completion.
    Surface(
        onClick = onEdit,
        color = if (dt.done) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .offset(x = LABEL_WIDTH + 4.dp, y = top)
            .padding(end = 12.dp)
            .fillMaxWidth()
            .height(h),
    ) {
        Row(Modifier.fillMaxSize().padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = dt.done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f)) {
                Text(
                    dt.task.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textDecoration = if (dt.done) TextDecoration.LineThrough else null,
                )
                Text(t.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NowLine() {
    val now = LocalTime.now()
    val y: Dp = HOUR_HEIGHT * ((now.hour * 60 + now.minute) / 60f)
    Box(
        Modifier
            .offset(y = y)
            .fillMaxWidth()
            .padding(start = LABEL_WIDTH)
            .height(2.dp)
            .background(Color.Red),
    )
}
