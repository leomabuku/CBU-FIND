package com.campus.lostandfound.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.lostandfound.data.model.Item
import com.campus.lostandfound.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ProfileViewModel(private val repository: AppRepository) : ViewModel() {
    private val _userItems = MutableStateFlow<List<Item>>(emptyList())
    val userItems: StateFlow<List<Item>> = _userItems.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun loadUserItems(userId: String) {
        viewModelScope.launch {
            repository.getItemsByUser(userId).collect { items ->
                _userItems.value = items
            }
        }
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
                val photoUrl = photoUri?.let {
                    repository.uploadImage(it, user.id, "profile")
                } ?: user.photoUrl
                repository.updateUser(user.copy(photoUrl = photoUrl))
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
