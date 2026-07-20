package com.example.dopapatch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dopapatch.ui.home.HomeViewModel
import com.example.dopapatch.ui.theme.DopaPatchTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DopaPatchTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    HomeScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(modifier: Modifier = Modifier, vm: HomeViewModel = viewModel(factory = HomeViewModel.Factory)) {
    // Phase 0 placeholder — later phases replace this with the Checklist / Time-blocks nav.
    val status = if (vm.backendConfigured) "Backend configured ✓" else "Set Supabase keys in local.properties"
    Text(text = "DopaPatch — $status", modifier = modifier)
}
