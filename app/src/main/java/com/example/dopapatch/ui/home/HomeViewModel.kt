package com.example.dopapatch.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.dopapatch.DopaPatchApp
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch

/** Placeholder "signed-in" screen VM. Phase 5/6 replace this with the real Checklist / Time-blocks nav. */
class HomeViewModel(private val auth: Auth) : ViewModel() {
    val userEmail: String? = (auth.sessionStatus.value as? SessionStatus.Authenticated)?.session?.user?.email

    fun signOut() = viewModelScope.launch { auth.signOut() }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as DopaPatchApp
                HomeViewModel(app.container.auth)
            }
        }
    }
}
