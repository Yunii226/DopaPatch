package com.example.dopapatch.data.alarm

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

/** The task-alarm channel. High importance so the full-screen intent can fire; silent because
 *  [AlarmService] plays the looping ringtone itself (avoids double sound). */
object Notifications {
    const val CHANNEL_ID = "task_alarms"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(NotificationManager::class.java)
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            mgr.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Task alarms", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Ringing reminders for scheduled tasks"
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
    }
}
