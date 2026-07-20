package com.example.dopapatch.ui.checklist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dopapatch.data.local.TaskEntity
import com.example.dopapatch.domain.timeblock.TimeBlock
import com.example.dopapatch.domain.timeblock.TimeBlocks
import java.time.LocalDate
import java.time.LocalTime

// Display order: untimed first, then chronological.
private val BLOCK_ORDER = listOf(
    TimeBlock.ANYTIME, TimeBlock.MORNING, TimeBlock.AFTERNOON, TimeBlock.EVENING, TimeBlock.NIGHT,
)

private fun TimeBlock.label() = when (this) {
    TimeBlock.ANYTIME -> "Anytime"
    TimeBlock.MORNING -> "Morning"
    TimeBlock.AFTERNOON -> "Afternoon"
    TimeBlock.EVENING -> "Evening"
    TimeBlock.NIGHT -> "Night"
}

/** The day's tasks grouped into time-blocks; the block containing *now* (only when viewing today) is marked. */
@Composable
fun TimeBlocksBody(
    ui: DayUiState,
    onToggle: (String) -> Unit,
    onEdit: (TaskEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (ui.totalCount == 0) {
        EmptyDay(modifier)
        return
    }
    val currentBlock = if (ui.date == LocalDate.now()) TimeBlocks.of(LocalTime.now()) else null
    val grouped = ui.all.groupBy { it.timeBlock }

    LazyColumn(modifier.fillMaxSize()) {
        BLOCK_ORDER.forEach { block ->
            val tasks = grouped[block].orEmpty()
            if (tasks.isEmpty()) return@forEach
            item(key = "block-$block") { BlockHeader(block.label(), highlighted = block == currentBlock) }
            items(tasks, key = { it.task.id }) { dt ->
                TaskRowContent(dt, onToggle = { onToggle(dt.task.id) }, onEdit = { onEdit(dt.task) })
            }
        }
    }
}

@Composable
private fun BlockHeader(label: String, highlighted: Boolean) {
    val text = if (highlighted) "$label · now" else label
    if (highlighted) {
        Text(
            text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primaryContainer)
                .padding(horizontal = 8.dp, vertical = 8.dp),
        )
    } else {
        SectionHeader(text)
    }
}
