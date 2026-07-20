package com.example.dopapatch.data.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.dopapatch.data.local.TaskEntity
import com.example.dopapatch.domain.alarm.nextOccurrence
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Schedules exact alarms for alarm-enabled tasks via [AlarmManager]. Each task gets one pending
 * alarm keyed by task id (request code = id.hashCode()); the next occurrence is scheduled, and
 * [AlarmReceiver] reschedules the following one when it fires.
 */
class AlarmScheduler(private val context: Context) {
    private val am = context.getSystemService(AlarmManager::class.java)

    /** Exact-alarm capability. USE_EXACT_ALARM (manifest) makes this true on API 33+. */
    fun canExact(): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    fun schedule(task: TaskEntity) {
        if (!task.alarmEnabled || task.scheduledTime == null || task.deletedAt != null) {
            cancel(task.id); return
        }
        val next = nextOccurrence(task, LocalDateTime.now()) ?: run { cancel(task.id); return }
        fireAt(task.id, task.title, next)
    }

    fun scheduleAll(tasks: List<TaskEntity>) = tasks.forEach(::schedule)

    fun snooze(taskId: String, title: String, day: Long, minutes: Long = 10) {
        setAlarm(taskId, firePendingIntent(taskId, title, day), LocalDateTime.now().plusMinutes(minutes))
    }

    fun cancel(taskId: String) {
        am.cancel(firePendingIntent(taskId, "", 0L))
    }

    private fun fireAt(taskId: String, title: String, at: LocalDateTime) {
        val day = at.toLocalDate().toEpochDay()
        setAlarm(taskId, firePendingIntent(taskId, title, day), at)
    }

    private fun setAlarm(taskId: String, pi: PendingIntent, at: LocalDateTime) {
        val triggerAt = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        // ponytail: inexact fallback when exact isn't permitted — still allowed while idle.
        if (canExact()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
    }

    private fun firePendingIntent(taskId: String, title: String, day: Long): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java).apply {
            action = AlarmReceiver.ACTION_FIRE
            putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
            putExtra(AlarmReceiver.EXTRA_TITLE, title)
            putExtra(AlarmReceiver.EXTRA_DAY, day)
        }
        return PendingIntent.getBroadcast(
            context, taskId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
