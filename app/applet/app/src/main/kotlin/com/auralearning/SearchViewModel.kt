package com.auralearning

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class SearchViewModel : ViewModel() {
    private val _uiState = MutableStateFlow<SearchUiState>(SearchUiState.Idle)
    val uiState: StateFlow<SearchUiState> = _uiState

    fun performSearch(query: String) {
        if (query.isBlank()) return

        if (isUrl(query)) {
            val url = if (query.startsWith("http://") || query.startsWith("https://")) {
                query
            } else {
                "https://$query"
            }
            _uiState.value = SearchUiState.OpenUrl(url)
        } else {
            // TODO: Search internal database
            _uiState.value = SearchUiState.SearchResults(query, emptyList())
        }
    }

    private fun isUrl(input: String): Boolean {
        return input.contains(".") || input.startsWith("http://") || input.startsWith("https://")
    }
}

sealed class SearchUiState {
    object Idle : SearchUiState()
    data class OpenUrl(val url: String) : SearchUiState()
    data class SearchResults(val query: String, val results: List<String>) : SearchUiState()
}
