package com.example.dopapatch.data.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Receives the fired AlarmManager broadcast and starts the foreground [AlarmService], which does
 * the actual ringing. (Exact-alarm apps are allowed to start a FGS from this broadcast.)
 * Done/Snooze are handled by the service/activity, not here.
 */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_FIRE) return
        val svc = Intent(context, AlarmService::class.java).apply {
            action = AlarmService.ACTION_START
            putExtra(EXTRA_TASK_ID, intent.getStringExtra(EXTRA_TASK_ID))
            putExtra(EXTRA_TITLE, intent.getStringExtra(EXTRA_TITLE).orEmpty())
            putExtra(EXTRA_DAY, intent.getLongExtra(EXTRA_DAY, 0L))
        }
        ContextCompat.startForegroundService(context, svc)
    }

    companion object {
        const val ACTION_FIRE = "com.example.dopapatch.ALARM_FIRE"
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_DAY = "day" // occurrence date as epoch-day
    }
}
