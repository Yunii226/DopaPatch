package com.example.dopapatch.ui.checklist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.dopapatch.domain.model.DayTask

/** Checkbox + title (+ time), struck-through when done. Tap the row body to edit. */
@Composable
fun TaskRowContent(
    dt: DayTask,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
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
