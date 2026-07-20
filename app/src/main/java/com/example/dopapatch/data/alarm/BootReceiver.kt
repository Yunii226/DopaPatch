package com.example.dopapatch.data.alarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.dopapatch.DopaPatchApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Alarms are cleared on reboot — reschedule everything from Room. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as DopaPatchApp).container
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.rescheduleAlarms()
            } finally {
                pending.finish()
            }
        }
    }
}
