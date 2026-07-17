package com.campus.lostandfound.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.lostandfound.data.model.ChatAttachment
import com.campus.lostandfound.data.model.ChatMessage
import com.campus.lostandfound.data.model.Conversation
import com.campus.lostandfound.data.repository.AppRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModel(private val repository: AppRepository) : ViewModel() {
    private val conversationId = MutableStateFlow("")
    private var currentUserId: String = ""

    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conversation.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> = conversationId
        .flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else repository.getMessages(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pendingAttachment = MutableStateFlow<ChatAttachment?>(null)
    val pendingAttachment: StateFlow<ChatAttachment?> = _pendingAttachment.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun load(id: String, userId: String) {
        if (conversationId.value == id && currentUserId == userId) return
        conversationId.value = id
        currentUserId = userId
        viewModelScope.launch {
            runCatching {
                _conversation.value = repository.getConversation(id)
                repository.markConversationRead(id, userId)
            }.onFailure { _error.value = it.message ?: "Could not open this conversation." }
        }
    }

    fun attach(uri: Uri) {
        if (_isUploading.value || currentUserId.isBlank()) return
        viewModelScope.launch {
            _isUploading.value = true
            _error.value = null
            try {
                _pendingAttachment.value = repository.uploadChatMedia(uri, currentUserId)
            } catch (e: Exception) {
                _error.value = e.message ?: "Could not upload that file."
            } finally {
                _isUploading.value = false
            }
        }
    }

    fun removeAttachment() {
        _pendingAttachment.value = null
    }

    fun markRead() {
        val id = conversationId.value
        if (id.isBlank() || currentUserId.isBlank()) return
        viewModelScope.launch {
            runCatching { repository.markConversationRead(id, currentUserId) }
        }
    }

    fun send(text: String, onSent: () -> Unit) {
        val id = conversationId.value
        if (id.isBlank() || currentUserId.isBlank() || _isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            _error.value = null
            try {
                repository.sendMessage(id, currentUserId, text, _pendingAttachment.value)
                _pendingAttachment.value = null
                onSent()
            } catch (e: Exception) {
                _error.value = e.message ?: "Could not send this message."
            } finally {
                _isSending.value = false
            }
        }
    }
}
