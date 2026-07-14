package com.campus.lostandfound.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.lostandfound.data.model.Item
import com.campus.lostandfound.data.model.ItemStatus
import com.campus.lostandfound.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ItemDetailsViewModel(private val repository: AppRepository) : ViewModel() {
    private val _item = MutableStateFlow<Item?>(null)
    val item: StateFlow<Item?> = _item.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun loadItem(itemId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _item.value = repository.getItemById(itemId)
                if (_item.value == null) _error.value = "This report is no longer available."
            } catch (e: Exception) {
                _error.value = e.message ?: "Could not load this report."
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun markAsResolved() {
        val currentItem = _item.value ?: return
        viewModelScope.launch {
            try {
                repository.markItemResolved(currentItem.id)
                _item.value = currentItem.copy(
                    status = ItemStatus.RESOLVED,
                    resolvedAt = System.currentTimeMillis()
                )
            } catch (e: Exception) {
                _error.value = e.message ?: "Could not mark this report as resolved."
            }
        }
    }
}
