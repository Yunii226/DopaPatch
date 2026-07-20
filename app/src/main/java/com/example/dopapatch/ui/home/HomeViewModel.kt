package com.example.dopapatch.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.dopapatch.DopaPatchApp
import com.example.dopapatch.di.AppConfig

/** Placeholder VM proving DI + BuildConfig are wired. Real screens replace this in later phases. */
class HomeViewModel(appConfig: AppConfig) : ViewModel() {
    val backendConfigured: Boolean = appConfig.isBackendConfigured

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as DopaPatchApp
                HomeViewModel(app.container.config)
            }
        }
    }
}
