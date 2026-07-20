package com.example.dopapatch.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.dopapatch.DopaPatchApp
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.exceptions.RestException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuthUiState(
    val isSignUp: Boolean = false,
    val submitting: Boolean = false,
    val error: String? = null,
    val info: String? = null, // e.g. "check your email" after sign-up confirmation
)

/** Owns the sign-in/sign-up form. The session gate itself observes [Auth.sessionStatus]. */
class AuthViewModel(private val auth: Auth) : ViewModel() {
    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    fun toggleMode() = _ui.update { AuthUiState(isSignUp = !it.isSignUp) }

    fun submit(email: String, password: String) {
        val e = email.trim()
        if (e.isBlank() || password.isBlank()) {
            _ui.update { it.copy(error = "Email and password are required.") }
            return
        }
        val signUp = _ui.value.isSignUp
        _ui.update { it.copy(submitting = true, error = null, info = null) }
        viewModelScope.launch {
            try {
                if (signUp) {
                    val session = auth.signUpWith(Email) { this.email = e; this.password = password }
                    // If email confirmation is on, signUp returns no active session.
                    if (session == null) _ui.update {
                        it.copy(submitting = false, info = "Account created — confirm via the email we sent, then sign in.", isSignUp = false)
                    } else _ui.update { it.copy(submitting = false) }
                } else {
                    auth.signInWith(Email) { this.email = e; this.password = password }
                    _ui.update { it.copy(submitting = false) }
                }
            } catch (ex: RestException) {
                _ui.update { it.copy(submitting = false, error = ex.message ?: "Authentication failed.") }
            } catch (ex: Exception) {
                _ui.update { it.copy(submitting = false, error = "Network error — check your connection.") }
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as DopaPatchApp
                AuthViewModel(app.container.auth)
            }
        }
    }
}
