package com.campus.lostandfound.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.lostandfound.data.model.ModerationCase
import com.campus.lostandfound.data.repository.AppRepository
import com.campus.lostandfound.data.repository.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ModerationViewModel(private val repository: AppRepository) : ViewModel() {
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    val cases: StateFlow<List<ModerationCase>> = repository.getModerationCases { _syncState.value = it }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _role = MutableStateFlow("USER")
    val role: StateFlow<String> = _role.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    fun load(uid: String) = viewModelScope.launch { try { _role.value = repository.getRole(uid) } catch (error: Exception) { _error.value = "Role could not be verified. Reference: ANDROID-ROLE" } }
    fun action(record: ModerationCase, action: String, targetId: String = record.targetId) = work { repository.moderationAction(record.id, action, targetId) }
    fun assignRole(uid: String, role: String) = work { repository.assignRole(uid, role) }
    private fun work(block: suspend () -> Unit) = viewModelScope.launch { _error.value = null; try { block() } catch (error: Exception) { _error.value = error.message ?: "Moderation action failed. Reference: ANDROID-MODERATION" } }
}
