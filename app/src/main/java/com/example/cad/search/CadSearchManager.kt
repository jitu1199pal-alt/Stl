package com.example.cad.search

import com.example.cad.model.CadDocument
import com.example.cad.model.CadTextMatch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SearchState(
    val query: String = "",
    val matches: List<CadTextMatch> = emptyList(),
    val currentIndex: Int = -1,
    val isSearching: Boolean = false
) {
    val totalMatches: Int get() = matches.size
    val currentMatch: CadTextMatch?
        get() = if (currentIndex in matches.indices) matches[currentIndex] else null
}

/**
 * Manages text search (Find Tool) within CAD documents.
 */
class CadSearchManager {
    private val _searchState = MutableStateFlow(SearchState())
    val searchState: StateFlow<SearchState> = _searchState.asStateFlow()

    fun performSearch(query: String, document: CadDocument?) {
        if (query.isBlank() || document == null) {
            _searchState.value = SearchState(query = query)
            return
        }

        val q = query.trim().lowercase()
        val matches = mutableListOf<CadTextMatch>()

        for (e in document.entities) {
            when (e) {
                is com.example.cad.model.CadEntity.Text -> {
                    if (e.text.lowercase().contains(q)) {
                        matches.add(CadTextMatch(e.text, e.position, e.objectId, e.layer))
                    }
                }
                is com.example.cad.model.CadEntity.MText -> {
                    if (e.text.lowercase().contains(q)) {
                        matches.add(CadTextMatch(e.text, e.position, e.objectId, e.layer))
                    }
                }
                is com.example.cad.model.CadEntity.Dimension -> {
                    if (e.text.lowercase().contains(q)) {
                        matches.add(CadTextMatch(e.text, e.definitionPoint, e.objectId, e.layer))
                    }
                }
                is com.example.cad.model.CadEntity.BlockReference -> {
                    for ((_, v) in e.attributes) {
                        if (v.lowercase().contains(q)) {
                            matches.add(CadTextMatch(v, e.insertionPoint, e.objectId, e.layer))
                        }
                    }
                }
                else -> {}
            }
        }

        _searchState.value = SearchState(
            query = query,
            matches = matches,
            currentIndex = if (matches.isNotEmpty()) 0 else -1,
            isSearching = false
        )
    }

    fun nextMatch() {
        val s = _searchState.value
        if (s.matches.isEmpty()) return
        val nextIdx = (s.currentIndex + 1) % s.matches.size
        _searchState.value = s.copy(currentIndex = nextIdx)
    }

    fun previousMatch() {
        val s = _searchState.value
        if (s.matches.isEmpty()) return
        val prevIdx = if (s.currentIndex - 1 < 0) s.matches.size - 1 else s.currentIndex - 1
        _searchState.value = s.copy(currentIndex = prevIdx)
    }

    fun clearSearch() {
        _searchState.value = SearchState()
    }
}
