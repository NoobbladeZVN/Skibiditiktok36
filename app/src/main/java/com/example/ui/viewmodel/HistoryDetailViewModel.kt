package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.SessionWithMessages
import com.example.data.repository.StudyRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "HistoryDetailViewModel"

class HistoryDetailViewModel(
    sessionId: Long,
    private val repository: StudyRepository
) : ViewModel() {

    val sessionState: StateFlow<SessionWithMessages?> = repository
        .observeSessionById(sessionId)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val _chatInput = MutableStateFlow("")
    val chatInput: StateFlow<String> = _chatInput.asStateFlow()

    private val _streamingReply = MutableStateFlow<String?>(null)
    val streamingReply: StateFlow<String?> = _streamingReply.asStateFlow()

    private val _isSendingChat = MutableStateFlow(false)
    val isSendingChat: StateFlow<Boolean> = _isSendingChat.asStateFlow()

    private val _chatError = MutableStateFlow<String?>(null)
    val chatError: StateFlow<String?> = _chatError.asStateFlow()

    fun updateChatInput(text: String) {
        _chatInput.value = text
    }

    fun clearChatError() {
        _chatError.value = null
    }

    fun sendFollowUp(questionOverride: String? = null) {
        val current = sessionState.value ?: return
        val question = (questionOverride ?: _chatInput.value).trim()
        if (question.isBlank() || _isSendingChat.value) return

        viewModelScope.launch {
            _isSendingChat.value = true
            _chatError.value = null
            if (questionOverride == null) {
                _chatInput.value = ""
            }
            val priorMessages = current.messages
            val session = current.session
            try {
                var latestReply = ""
                repository.streamFollowUpReply(session, priorMessages, question)
                    .collect { partial ->
                        latestReply = partial
                        _streamingReply.value = partial
                    }
                if (latestReply.isNotBlank()) {
                    repository.saveChatTurn(session.id, isUser = true, content = question)
                    repository.saveChatTurn(session.id, isUser = false, content = latestReply)
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Follow-up chat timed out", e)
                if (questionOverride == null && _chatInput.value.isBlank()) {
                    _chatInput.value = question
                }
                _chatError.value = StudyRepository.toFriendlyVietnameseError(e)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Follow-up chat failed", e)
                if (questionOverride == null && _chatInput.value.isBlank()) {
                    _chatInput.value = question
                }
                _chatError.value = StudyRepository.toFriendlyVietnameseError(e)
            } finally {
                _streamingReply.value = null
                _isSendingChat.value = false
            }
        }
    }

    class Factory(
        private val sessionId: Long,
        private val repository: StudyRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return HistoryDetailViewModel(sessionId, repository) as T
        }
    }
}
