package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SessionWithMessages
import com.example.data.local.SolvedSessionEntity
import com.example.data.repository.StudyRepository
import com.example.model.EducationLevel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repository: StudyRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilterLevel = MutableStateFlow<EducationLevel?>(null)
    val selectedFilterLevel: StateFlow<EducationLevel?> = _selectedFilterLevel.asStateFlow()

    val totalCount: StateFlow<Int> = repository.observeAllSessions()
        .combine(_searchQuery) { list, _ -> list.size }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = 0
        )

    val filteredSessions: StateFlow<List<SessionWithMessages>> = combine(
        repository.observeAllSessions(),
        _searchQuery,
        _selectedFilterLevel
    ) { sessions, query, levelFilter ->
        val normalizedQuery = query.trim().lowercase()
        sessions.filter { item ->
            val matchesLevel = levelFilter == null || item.session.educationLevel == levelFilter.name
            val matchesQuery = normalizedQuery.isBlank() ||
                item.session.title.lowercase().contains(normalizedQuery) ||
                item.session.subject.lowercase().contains(normalizedQuery) ||
                item.session.problemText.lowercase().contains(normalizedQuery) ||
                item.session.finalAnswer.lowercase().contains(normalizedQuery)
            matchesLevel && matchesQuery
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun selectFilterLevel(level: EducationLevel?) {
        _selectedFilterLevel.value = level
    }

    fun deleteSession(session: SolvedSessionEntity) {
        viewModelScope.launch {
            repository.deleteSession(session)
        }
    }

    class Factory(
        private val repository: StudyRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryViewModel(repository) as T
        }
    }
}
