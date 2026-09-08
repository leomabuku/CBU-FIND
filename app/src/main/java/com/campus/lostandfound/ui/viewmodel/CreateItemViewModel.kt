package com.campus.lostandfound.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.lostandfound.data.model.Item
import com.campus.lostandfound.data.model.ItemType
import com.campus.lostandfound.data.repository.AppRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class CreateItemViewModel(private val repository: AppRepository) : ViewModel() {

    private val _isSubmitting = MutableStateFlow(false)
    val isSubmitting: StateFlow<Boolean> = _isSubmitting.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _progressMessage = MutableStateFlow<String?>(null)
    val progressMessage: StateFlow<String?> = _progressMessage.asStateFlow()

    fun clearError() {
        _error.value = null
    }

    fun createItem(
        type: ItemType,
        title: String,
        description: String,
        category: String,
        location: String,
        contactInfo: String,
        userId: String,
        imageUris: List<Uri>,
        onSuccess: () -> Unit
    ) {
        if (userId.isBlank()) {
            _error.value = "Please sign in before creating a report."
            return
        }
        viewModelScope.launch {
            _isSubmitting.value = true
            _error.value = null
            try {
                val media = imageUris.mapIndexed { index, uri ->
                    _progressMessage.value = "Uploading image ${index + 1} of ${imageUris.size}…"
                    repository.uploadImage(uri, "reports")
                }
                _progressMessage.value = "Publishing report…"
                val item = Item(
                    type = type,
                    title = title.trim(),
                    description = description.trim(),
                    category = category,
                    location = location.trim(),
                    media = media,
                    imageUrls = media.map { it.secureUrl },
                    imageUri = media.firstOrNull()?.secureUrl,
                    date = System.currentTimeMillis(),
                    userId = userId,
                    contactInfo = contactInfo.trim()
                )
                repository.insertItem(item)
                onSuccess()
            } catch (e: Exception) {
                _error.value = e.message ?: "Could not publish this report."
            } finally {
                _isSubmitting.value = false
                _progressMessage.value = null
            }
        }
    }
}
