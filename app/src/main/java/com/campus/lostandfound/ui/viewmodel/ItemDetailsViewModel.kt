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
    private val _isWorking = MutableStateFlow(false)
    val isWorking: StateFlow<Boolean> = _isWorking.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _contact = MutableStateFlow<String?>(null)
    val contact: StateFlow<String?> = _contact.asStateFlow()

    fun loadItem(itemId: String) = viewModelScope.launch {
        _isLoading.value = true; _error.value = null
        try { _item.value = repository.getItemById(itemId); if (_item.value == null) _error.value = "This report is no longer available." }
        catch (error: Exception) { _error.value = error.message ?: "Could not load this report. Reference: ANDROID-REPORT" }
        finally { _isLoading.value = false }
    }

    fun changeResolvedState() {
        val current = _item.value ?: return
        viewModelScope.launch {
            _isWorking.value = true; _error.value = null
            try {
                if (current.status == ItemStatus.RESOLVED) repository.reopenItem(current.id) else repository.markItemResolved(current.id)
                _item.value = current.copy(status = if (current.status == ItemStatus.RESOLVED) ItemStatus.ACTIVE else ItemStatus.RESOLVED, resolvedAt = if (current.status == ItemStatus.RESOLVED) null else System.currentTimeMillis())
            } catch (error: Exception) { _error.value = error.message ?: "Could not update this report. Reference: ANDROID-REPORT-STATUS" }
            finally { _isWorking.value = false }
        }
    }

    fun submitClaim(note: String, onSubmitted: () -> Unit) {
        val current = _item.value ?: return
        viewModelScope.launch {
            _isWorking.value = true; _error.value = null
            try { repository.createClaim(current, note); onSubmitted() }
            catch (error: Exception) { _error.value = error.message ?: "Could not submit your claim. Reference: ANDROID-CLAIM" }
            finally { _isWorking.value = false }
        }
    }

    fun loadContact() {
        val current = _item.value ?: return
        viewModelScope.launch {
            _isWorking.value = true; _error.value = null
            try { _contact.value = repository.getReportContact(current.id) }
            catch (error: Exception) { _error.value = error.message ?: "Could not load private contact details. Reference: ANDROID-REPORT-CONTACT" }
            finally { _isWorking.value = false }
        }
    }
}
