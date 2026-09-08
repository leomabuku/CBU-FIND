package com.campus.lostandfound.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.lostandfound.data.model.Claim
import com.campus.lostandfound.data.model.ClaimStatus
import com.campus.lostandfound.data.repository.AppRepository
import com.campus.lostandfound.data.repository.SyncState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ClaimsViewModel(private val repository: AppRepository) : ViewModel() {
    private val userId = MutableStateFlow("")
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()
    private val statusOverrides = MutableStateFlow<Map<String, ClaimStatus>>(emptyMap())
    private val serverClaims = userId.flatMapLatest { if (it.isBlank()) flowOf(emptyList()) else repository.getClaims(it) { state -> _syncState.value = state } }
    val claims: StateFlow<List<Claim>> = combine(serverClaims, statusOverrides) { claims, overrides ->
        claims.map { claim -> overrides[claim.id]?.let { claim.copy(status = it) } ?: claim }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error
    private val _workingId = MutableStateFlow<String?>(null)
    val workingId: StateFlow<String?> = _workingId
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message
    fun load(uid: String) { userId.value = uid }
    fun action(claimId: String, action: String, onConversation: (String) -> Unit = {}) = viewModelScope.launch {
        _workingId.value = claimId; _error.value = null; _message.value = null
        try {
            val result = repository.actOnClaim(claimId, action)
            statusOverrides.update { it + (claimId to result.status) }
            _message.value = when (result.status) {
                ClaimStatus.ACCEPTED -> "Claim accepted. Your private conversation is ready."
                ClaimStatus.REJECTED -> "Claim rejected. The claimant has been notified."
                ClaimStatus.CANCELLED -> "Claim cancelled."
                ClaimStatus.PENDING -> "Claim updated."
            }
            result.conversationId?.let(onConversation)
        }
        catch (error: Exception) { _error.value = error.message ?: "Could not update the claim. Reference: ANDROID-CLAIM-ACTION" }
        finally { _workingId.value = null }
    }
}
