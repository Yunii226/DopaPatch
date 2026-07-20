package com.example.dopapatch.data.alarm

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import com.example.dopapatch.DopaPatchApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * A real alarm: a foreground service that loops the alarm ringtone + vibrates until dismissed,
 * and posts a full-screen-intent notification that opens [AlarmActivity] over the lock screen.
 * Started from [AlarmReceiver] on fire; stopped by Dismiss/Snooze (from the activity or the
 * notification actions). Sound plays regardless of lock state because the service owns it.
 */
class AlarmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra(AlarmReceiver.EXTRA_TASK_ID)
        val title = intent?.getStringExtra(AlarmReceiver.EXTRA_TITLE).orEmpty()
        val day = intent?.getLongExtra(AlarmReceiver.EXTRA_DAY, 0L) ?: 0L
        val container = (applicationContext as DopaPatchApp).container

        when (intent?.action) {
            ACTION_START -> if (taskId != null) startRinging(taskId, title, day)
            ACTION_DONE -> stopWith { container.completionRepository.markDone(taskId!!, LocalDate.ofEpochDay(day)); container.syncManager.sync() }
            ACTION_SNOOZE -> stopWith { container.alarmScheduler.snooze(taskId!!, title, day) }
            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun startRinging(taskId: String, title: String, day: Long) {
        startForegroundCompat(taskId.hashCode(), buildNotification(taskId, title, day))
        playAlarm()
        vibrate()
        scope.launch { (applicationContext as DopaPatchApp).container.rescheduleTask(taskId) } // queue next occurrence
    }

    private fun stopWith(action: suspend () -> Unit) {
        stopSoundAndVibration()
        scope.launch {
            runCatching { action() }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun buildNotification(taskId: String, title: String, day: Long): android.app.Notification {
        val full = PendingIntent.getActivity(
            this, taskId.hashCode(),
            Intent(this, AlarmActivity::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
                putExtra(AlarmReceiver.EXTRA_TITLE, title)
                putExtra(AlarmReceiver.EXTRA_DAY, day)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title.ifBlank { "Task" })
            .setContentText("Alarm")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(full, true) // launches the alarm screen over the lock screen
            .setContentIntent(full)
            .addAction(0, "Done", serviceAction(ACTION_DONE, taskId, title, day))
            .addAction(0, "Snooze", serviceAction(ACTION_SNOOZE, taskId, title, day))
            .build()
    }

    private fun serviceAction(action: String, taskId: String, title: String, day: Long): PendingIntent {
        val intent = Intent(this, AlarmService::class.java).apply {
            this.action = action
            putExtra(AlarmReceiver.EXTRA_TASK_ID, taskId)
            putExtra(AlarmReceiver.EXTRA_TITLE, title)
            putExtra(AlarmReceiver.EXTRA_DAY, day)
        }
        val code = (action + taskId).hashCode()
        return PendingIntent.getService(this, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    private fun startForegroundCompat(id: Int, notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(id, notification)
        }
    }

    private fun playAlarm() {
        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE) ?: return
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            setDataSource(this@AlarmService, uri)
            isLooping = true
            prepare()
            start()
        }
    }

    private fun vibrate() {
        val v = if (Build.VERSION.SDK_INT >= 31) {
            (getSystemService(VibratorManager::class.java)).defaultVibrator
        } else {
            @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
        }
        vibrator = v
        val pattern = longArrayOf(0, 700, 700)
        if (Build.VERSION.SDK_INT >= 26) {
            v.vibrate(VibrationEffect.createWaveform(pattern, 0))
        } else {
            @Suppress("DEPRECATION") v.vibrate(pattern, 0)
        }
    }

    private fun stopSoundAndVibration() {
        runCatching { player?.stop(); player?.release() }
        player = null
        vibrator?.cancel()
        vibrator = null
    }

    override fun onDestroy() {
        stopSoundAndVibration()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.dopapatch.ALARM_SVC_START"
        const val ACTION_DONE = "com.example.dopapatch.ALARM_SVC_DONE"
        const val ACTION_SNOOZE = "com.example.dopapatch.ALARM_SVC_SNOOZE"
    }
}
