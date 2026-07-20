package com.example.dopapatch.data.alarm

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** Task-alarm notification channel + builder with Done / Snooze actions. */
object Notifications {
    const val CHANNEL_ID = "task_alarms"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Task alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Reminders for scheduled tasks"
                },
            )
        }
    }

    fun show(context: Context, taskId: String, title: String, occurredEpochDay: Long) {
        if (!canPost(context)) return
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title.ifBlank { "Task" })
            .setContentText("It's time.")
            .setPriority(NotificationCompat.PRIORITY_HIGH) // heads-up on < API 26
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .addAction(0, "Done", action(context, AlarmReceiver.ACTION_DONE, taskId, title, occurredEpochDay))
            .addAction(0, "Snooze", action(context, AlarmReceiver.ACTION_SNOOZE, taskId, title, occurredEpochDay))
            .build()
        NotificationManagerCompat.from(context).notify(taskId.hashCode(), n)
    }

    fun cancel(context: Context, taskId: String) =
        NotificationManagerCompat.from(context).cancel(taskId.hashCode())

    private fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun action(context: Context, act: String, taskId: String, title: String, day: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = act
            putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
            putExtra(AlarmReceiver.EXTRA_TITLE, title)
            putExtra(AlarmReceiver.EXTRA_DAY, day)
        }
        // Distinct request code per (action, task) so Done and Snooze don't collide.
        val code = (act + taskId).hashCode()
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
