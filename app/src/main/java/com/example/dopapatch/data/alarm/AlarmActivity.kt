package com.example.dopapatch.data.alarm

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.dopapatch.ui.theme.DopaPatchTheme
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Full-screen alarm shown over the lock screen; Dismiss/Snooze tell [AlarmService] to stop. */
class AlarmActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()

        val taskId = intent.getStringExtra(AlarmReceiver.EXTRA_TASK_ID)
        val title = intent.getStringExtra(AlarmReceiver.EXTRA_TITLE).orEmpty()
        val day = intent.getLongExtra(AlarmReceiver.EXTRA_DAY, 0L)

        setContent {
            DopaPatchTheme {
                Surface(Modifier.fillMaxSize()) {
                    AlarmScreen(
                        title = title,
                        onDone = { sendToService(AlarmService.ACTION_DONE, taskId, title, day) },
                        onSnooze = { sendToService(AlarmService.ACTION_SNOOZE, taskId, title, day) },
                    )
                }
            }
        }
    }

    private fun sendToService(action: String, taskId: String?, title: String, day: Long) {
        startService(
            Intent(this, AlarmService::class.java).apply {
                this.action = action
                putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
                putExtra(AlarmReceiver.EXTRA_TITLE, title)
                putExtra(AlarmReceiver.EXTRA_DAY, day)
            },
        )
        finish()
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@Composable
private fun AlarmScreen(title: String, onDone: () -> Unit, onSnooze: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")), style = MaterialTheme.typography.displayMedium)
        Text(title.ifBlank { "Task" }, style = MaterialTheme.typography.headlineSmall)
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        OutlinedButton(onClick = onSnooze, modifier = Modifier.fillMaxWidth()) { Text("Snooze 10 min") }
    }
}
