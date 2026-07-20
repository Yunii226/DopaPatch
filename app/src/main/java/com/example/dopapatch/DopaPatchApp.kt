package com.example.dopapatch

import android.app.Application
import com.example.dopapatch.di.AppContainer

class DopaPatchApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer()
    }
}
