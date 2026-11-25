package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.ImageUtils
import com.example.data.local.SessionWithMessages
import com.example.data.repository.StudyRepository
import com.example.model.EducationLevel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

private const val TAG = "SolveViewModel"

sealed interface SolveStatus {
    data object Idle : SolveStatus
    data object Loading : SolveStatus
    data object Success : SolveStatus
    data class Error(val message: String) : SolveStatus
}

@OptIn(ExperimentalCoroutinesApi::class)
class SolveViewModel(
    application: Application,
    private val repository: StudyRepository
) : AndroidViewModel(application) {

    private val _selectedLevel = MutableStateFlow(EducationLevel.HIGH_SCHOOL)
    val selectedLevel: StateFlow<EducationLevel> = _selectedLevel.asStateFlow()

    private val _problemText = MutableStateFlow("")
    val problemText: StateFlow<String> = _problemText.asStateFlow()

    private val _pendingImageUri = MutableStateFlow<Uri?>(null)
    val pendingImageUri: StateFlow<Uri?> = _pendingImageUri.asStateFlow()

    private val _solveStatus = MutableStateFlow<SolveStatus>(SolveStatus.Idle)
    val solveStatus: StateFlow<SolveStatus> = _solveStatus.asStateFlow()

    private val _activeSessionId = MutableStateFlow<Long?>(null)

    val activeSession: StateFlow<SessionWithMessages?> = _activeSessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.observeSessionById(id)
        }
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

    private val _inputWarning = MutableStateFlow<String?>(null)
    val inputWarning: StateFlow<String?> = _inputWarning.asStateFlow()

    fun selectEducationLevel(level: EducationLevel) {
        _selectedLevel.value = level
    }

    fun updateProblemText(text: String) {
        _problemText.value = text
        if (_inputWarning.value != null) _inputWarning.value = null
    }

    fun setPendingImage(uri: Uri?) {
        _pendingImageUri.value = uri
        if (uri != null && _inputWarning.value != null) _inputWarning.value = null
    }

    fun clearInputWarning() {
        _inputWarning.value = null
    }

    fun clearChatError() {
        _chatError.value = null
    }

    fun updateChatInput(text: String) {
        _chatInput.value = text
    }

    fun solveProblem() {
        val text = _problemText.value.trim()
        val imageUri = _pendingImageUri.value

        if (text.isBlank() && imageUri == null) {
            _inputWarning.value = "Vui lòng chụp/tải ảnh đề bài hoặc nhập nội dung câu hỏi."
            return
        }

        if (_solveStatus.value is SolveStatus.Loading) return

        viewModelScope.launch {
            _solveStatus.value = SolveStatus.Loading
            _chatError.value = null
            _streamingReply.value = null
            var savedImagePath: String? = null
            try {
                if (imageUri != null) {
                    savedImagePath = ImageUtils.saveProblemImageLocally(getApplication(), imageUri)
                }
                val newSessionId = repository.solveAndSaveProblem(
                    level = _selectedLevel.value,
                    problemText = text,
                    localImagePath = savedImagePath
                )
                _activeSessionId.value = newSessionId
                _solveStatus.value = SolveStatus.Success
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Solve timed out after retries and fallback", e)
                ImageUtils.deleteProblemImage(savedImagePath)
                _solveStatus.value = SolveStatus.Error(
                    StudyRepository.toFriendlyVietnameseError(e)
                )
            } catch (e: CancellationException) {
                ImageUtils.deleteProblemImage(savedImagePath)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed to solve problem", e)
                ImageUtils.deleteProblemImage(savedImagePath)
                _solveStatus.value = SolveStatus.Error(
                    StudyRepository.toFriendlyVietnameseError(e)
                )
            }
        }
    }

    fun sendFollowUp(questionOverride: String? = null) {
        val sessionWithMessages = activeSession.value ?: return
        val question = (questionOverride ?: _chatInput.value).trim()
        if (question.isBlank() || _isSendingChat.value) return

        viewModelScope.launch {
            _isSendingChat.value = true
            _chatError.value = null
            if (questionOverride == null) {
                _chatInput.value = ""
            }
            val priorMessages = sessionWithMessages.messages
            val session = sessionWithMessages.session
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

    fun startNewProblem() {
        _activeSessionId.value = null
        _problemText.value = ""
        _pendingImageUri.value = null
        _solveStatus.value = SolveStatus.Idle
        _chatInput.value = ""
        _streamingReply.value = null
        _chatError.value = null
        _inputWarning.value = null
    }

    class Factory(
        private val application: Application,
        private val repository: StudyRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SolveViewModel(application, repository) as T
        }
    }
}
