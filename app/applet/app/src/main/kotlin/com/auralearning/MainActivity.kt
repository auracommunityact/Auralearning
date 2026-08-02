package com.auralearning

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                SearchScreen()
            }
        }
    }
}

@Composable
fun SearchScreen(viewModel: SearchViewModel = viewModel()) {
    var query by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        TextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search Aura Learning") }
        )
        Button(onClick = { viewModel.performSearch(query) }) {
            Text("Search")
        }

        when (val state = uiState) {
            is SearchUiState.OpenUrl -> {
                Text("Opening URL: ${state.url}")
                // TODO: Implement InAppBrowser
            }
            is SearchUiState.SearchResults -> {
                Text("Searching for: ${state.query}")
                // TODO: Implement results list
            }
            else -> {}
        }
    }
}
