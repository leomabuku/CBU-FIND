package com.campus.lostandfound.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.lostandfound.data.model.ChatAttachment
import com.campus.lostandfound.data.model.ChatMessage
import com.campus.lostandfound.data.model.Conversation
import com.campus.lostandfound.data.model.Item
import com.campus.lostandfound.data.model.ItemStatus
import com.campus.lostandfound.data.repository.AppRepository
import com.campus.lostandfound.data.repository.SyncState
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
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    private val conversationId = MutableStateFlow("")
    private var currentUserId: String = ""

    private val _conversation = MutableStateFlow<Conversation?>(null)
    val conversation: StateFlow<Conversation?> = _conversation.asStateFlow()

    private val _report = MutableStateFlow<Item?>(null)
    val report: StateFlow<Item?> = _report.asStateFlow()

    val messages: StateFlow<List<ChatMessage>> = conversationId
        .flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else repository.getMessages(id) { _syncState.value = it } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _pendingAttachment = MutableStateFlow<ChatAttachment?>(null)
    val pendingAttachment: StateFlow<ChatAttachment?> = _pendingAttachment.asStateFlow()

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _isUploading = MutableStateFlow(false)
    val isUploading: StateFlow<Boolean> = _isUploading.asStateFlow()

    private val _isUpdatingReport = MutableStateFlow(false)
    val isUpdatingReport: StateFlow<Boolean> = _isUpdatingReport.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun load(id: String, userId: String) {
        if (conversationId.value == id && currentUserId == userId) return
        conversationId.value = id
        currentUserId = userId
        viewModelScope.launch {
            _error.value = null
            _notice.value = null
            _report.value = null
            try {
                val loadedConversation = repository.getConversation(id)
                _conversation.value = loadedConversation
                _report.value = loadedConversation?.itemId?.takeIf { it.isNotBlank() }?.let { itemId ->
                    runCatching { repository.getItemById(itemId) }.getOrNull()
                }
                repository.markConversationRead(id)
            } catch (error: Exception) {
                _error.value = error.message ?: "Could not open this conversation. Reference: ANDROID-CONVERSATION"
            }
        }
    }

    fun markReportResolved() {
        val current = _report.value ?: return
        if (current.userId != currentUserId || current.status != ItemStatus.MATCHED || _isUpdatingReport.value) return
        viewModelScope.launch {
            _isUpdatingReport.value = true
            _error.value = null
            _notice.value = null
            try {
                repository.markItemResolved(current.id)
                val now = System.currentTimeMillis()
                _report.value = current.copy(status = ItemStatus.RESOLVED, resolvedAt = now, updatedAt = now)
                _notice.value = "Report marked resolved. Thanks for closing the loop."
            } catch (error: Exception) {
                _error.value = error.message ?: "Could not mark this report resolved. Reference: ANDROID-REPORT-RESOLVE"
            } finally {
                _isUpdatingReport.value = false
            }
        }
    }

    fun attach(uri: Uri) {
        if (_isUploading.value || currentUserId.isBlank()) return
        viewModelScope.launch {
            _isUploading.value = true
            _error.value = null
            try {
                _pendingAttachment.value = repository.uploadChatMedia(uri)
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
            runCatching { repository.markConversationRead(id) }
        }
    }

    fun send(text: String, onSent: () -> Unit) {
        val id = conversationId.value
        if (id.isBlank() || currentUserId.isBlank() || _isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            _error.value = null
            try {
                repository.sendMessage(id, text, _pendingAttachment.value)
                _pendingAttachment.value = null
                onSent()
            } catch (e: Exception) {
                _error.value = e.message ?: "Could not send this message."
            } finally {
                _isSending.value = false
            }
        }
    }

    fun editMessage(messageId: String, text: String, onEdited: () -> Unit) {
        val id = conversationId.value
        if (id.isBlank() || messageId.isBlank() || text.isBlank() || _isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            _error.value = null
            try {
                repository.editMessage(id, messageId, text)
                onEdited()
            } catch (error: Exception) {
                _error.value = error.message ?: "Could not edit this message. Reference: ANDROID-MESSAGE-EDIT"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun deleteMessage(messageId: String) {
        val id = conversationId.value
        if (id.isBlank() || messageId.isBlank() || _isSending.value) return
        viewModelScope.launch {
            _isSending.value = true
            _error.value = null
            try {
                repository.deleteMessage(id, messageId)
            } catch (error: Exception) {
                _error.value = error.message ?: "Could not delete this message. Reference: ANDROID-MESSAGE-DELETE"
            } finally {
                _isSending.value = false
            }
        }
    }

    fun blockOtherParticipant() {
        val other = _conversation.value?.participantIds?.firstOrNull { it != currentUserId } ?: return
        viewModelScope.launch {
            try { repository.block(other); _error.value = "This account is now blocked. Existing history remains visible." }
            catch (error: Exception) { _error.value = error.message ?: "Could not block this account. Reference: ANDROID-BLOCK" }
        }
    }

    fun reportMessage(messageId: String, details: String) {
        val id = conversationId.value
        if (id.isBlank()) return
        viewModelScope.launch {
            try { repository.reportAbuse("MESSAGE", messageId, id, "OTHER", details); _error.value = "Report submitted. Moderators receive only this message and a small adjacent context window." }
            catch (error: Exception) { _error.value = error.message ?: "Could not submit the report. Reference: ANDROID-ABUSE-REPORT" }
        }
    }
}
