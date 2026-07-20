package com.example.dopapatch.data.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.dopapatch.DopaPatchApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Handles a fired alarm and its notification actions. Fire → post notification + schedule the
 * next occurrence. Done → mark complete + cancel. Snooze → re-fire in 10 min.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getStringExtra(EXTRA_TASK_ID) ?: return
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        val day = intent.getLongExtra(EXTRA_DAY, 0L)
        val container = (context.applicationContext as DopaPatchApp).container
        val pending = goAsync() // let async work finish after onReceive returns

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_FIRE -> {
                        Notifications.show(context, taskId, title, day)
                        container.rescheduleTask(taskId) // next occurrence for recurrent; no-op for spent events
                    }
                    ACTION_DONE -> {
                        container.completionRepository.markDone(taskId, LocalDate.ofEpochDay(day))
                        Notifications.cancel(context, taskId)
                        container.syncManager.sync()
                    }
                    ACTION_SNOOZE -> {
                        Notifications.cancel(context, taskId)
                        container.alarmScheduler.snooze(taskId, title, day)
                    }
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.example.dopapatch.ALARM_FIRE"
        const val ACTION_DONE = "com.example.dopapatch.ALARM_DONE"
        const val ACTION_SNOOZE = "com.example.dopapatch.ALARM_SNOOZE"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DAY = "day" // occurrence date as epoch-day
    }
}
