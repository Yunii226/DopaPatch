package com.example.dopapatch.di

import com.example.dopapatch.BuildConfig

/** Config sourced from BuildConfig (populated from local.properties). */
data class AppConfig(
    val supabaseUrl: String,
    val supabaseAnonKey: String,
    val geminiApiKey: String,
) {
    val isBackendConfigured: Boolean get() = supabaseUrl.isNotBlank() && supabaseAnonKey.isNotBlank()
}

/**
 * Manual DI container — app-wide singletons live here, created once in [DopaPatchApp].
 * Hilt's Gradle plugin is incompatible with AGP 9 (google/dagger#4944), and a single-module
 * personal app doesn't need a DI framework. Add repositories/clients here as later phases land
 * (e.g. `val taskRepository by lazy { ... }`).
 */
class AppContainer {
    val config = AppConfig(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY,
        geminiApiKey = BuildConfig.GEMINI_API_KEY,
    )
}
