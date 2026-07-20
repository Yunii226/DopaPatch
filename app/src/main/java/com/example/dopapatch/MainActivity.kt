package com.example.dopapatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dopapatch.ui.auth.AuthScreen
import com.example.dopapatch.ui.home.HomeViewModel
import com.example.dopapatch.ui.theme.DopaPatchTheme
import io.github.jan.supabase.auth.status.SessionStatus

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DopaPatchTheme {
                DopaPatchRoot()
            }
        }
    }
}

@Composable
private fun DopaPatchRoot() {
    val app = LocalContext.current.applicationContext as DopaPatchApp
    if (!app.container.config.isBackendConfigured) {
        Scaffold(modifier = Modifier.fillMaxSize()) { p ->
            Box(Modifier.fillMaxSize().padding(p), Alignment.Center) {
                Text("Set SUPABASE_URL and SUPABASE_ANON_KEY in local.properties.")
            }
        }
        return
    }

    // Session gate: Room isn't involved yet — auth state drives which screen shows.
    val status by app.container.auth.sessionStatus.collectAsState()
    when (status) {
        is SessionStatus.Initializing -> Loading()
        is SessionStatus.Authenticated -> MainScreen()
        // NotAuthenticated or RefreshFailure → back to the auth screen.
        else -> Scaffold(modifier = Modifier.fillMaxSize()) { p ->
            AuthScreen(modifier = Modifier.padding(p))
        }
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("DopaPatch") },
                actions = {
                    TextButton(onClick = { vm.signOut() }) { Text("Sign out") }
                },
            )
        },
    ) { p ->
        Box(Modifier.fillMaxSize().padding(p), Alignment.Center) {
            Text(
                "Signed in as ${vm.userEmail ?: "?"} — checklist lands in Phase 5.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}
