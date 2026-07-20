package com.example.dopapatch

import android.app.Application
import com.example.dopapatch.data.alarm.Notifications
import com.example.dopapatch.data.sync.SyncWorker
import com.example.dopapatch.di.AppContainer

class DopaPatchApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.ensureChannel(this)
        // Kick a sync on launch + keep a periodic one. Both no-op when signed out or unconfigured.
        if (container.config.isBackendConfigured) SyncWorker.schedule(this)
    }
}
