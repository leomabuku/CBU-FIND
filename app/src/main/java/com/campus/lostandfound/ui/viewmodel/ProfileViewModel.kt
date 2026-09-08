package com.campus.lostandfound.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.lostandfound.data.model.Item
import com.campus.lostandfound.data.repository.AppRepository
import com.campus.lostandfound.data.repository.SyncState
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(private val repository: AppRepository) : ViewModel() {
    private val _userItems = MutableStateFlow<List<Item>>(emptyList())
    val userItems: StateFlow<List<Item>> = _userItems.asStateFlow()

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var itemsJob: Job? = null
    private var loadedUserId: String? = null

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun loadUserItems(userId: String) {
        if (userId == loadedUserId && itemsJob?.isActive == true) return
        loadedUserId = userId
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            repository.getItemsByUser(userId) { state -> _syncState.value = state }.collect { items ->
                _userItems.value = items
            }
        }
    }

    fun retryUserItems() {
        val userId = loadedUserId ?: return
        loadedUserId = null
        loadUserItems(userId)
    }

    fun updateProfile(
        user: com.campus.lostandfound.data.model.User,
        photoUri: Uri?,
        onSaved: () -> Unit
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            _message.value = null
            try {
                val photoAsset = photoUri?.let { repository.uploadImage(it, "profiles") } ?: user.photoAsset
                repository.updateUser(user.copy(photoUrl = photoAsset?.secureUrl ?: user.photoUrl, photoAsset = photoAsset))
                _message.value = "Profile updated"
                onSaved()
            } catch (e: Exception) {
                _message.value = e.message ?: "Could not update profile"
            } finally {
                _isSaving.value = false
            }
        }
    }
}
