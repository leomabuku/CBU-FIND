package com.campus.lostandfound.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.lostandfound.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: AppRepository) : ViewModel() {
    private val _preferences = MutableStateFlow(mapOf("claims" to true, "claimDecisions" to true, "messages" to true, "reportUpdates" to true, "moderation" to true, "showMessagePreview" to false))
    val preferences: StateFlow<Map<String, Boolean>> = _preferences.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()
    private val _blockedAccounts = MutableStateFlow<List<String>>(emptyList())
    val blockedAccounts: StateFlow<List<String>> = _blockedAccounts.asStateFlow()
    init { refreshPreferences(); refreshBlocks() }
    fun toggle(key: String, value: Boolean) { _preferences.value = _preferences.value + (key to value) }
    fun save() = work { repository.saveNotificationPreferences(_preferences.value); _message.value = "Notification preferences saved." }
    fun registerToken(token: String) = work { repository.registerDeviceToken(token); _message.value = "Push notifications are enabled on this device." }
    fun reportPushPermissionDenied() { _error.value = "Notification permission was not granted. Allow notifications in Android settings, then retry. Reference: ANDROID-PUSH-PERMISSION" }
    fun reportPushTokenFailure(@Suppress("UNUSED_PARAMETER") error: Exception) { _error.value = "This device could not register for push notifications. Check Google Play services and your connection, then retry. Reference: ANDROID-PUSH-TOKEN" }
    fun deleteAccount(onQueued: () -> Unit) = work { val reference = repository.requestAccountDeletion(); _message.value = "Deletion queued. Reference: $reference"; onQueued() }
    fun unblock(uid: String) = work { repository.unblock(uid); _blockedAccounts.value = _blockedAccounts.value - uid; _message.value = "Account unblocked." }
    private fun refreshPreferences() = viewModelScope.launch { runCatching { repository.getNotificationPreferences() }.onSuccess { _preferences.value = it }.onFailure { _error.value = it.message ?: "Notification preferences could not load. Reference: ANDROID-PREFERENCES" } }
    private fun refreshBlocks() = viewModelScope.launch { runCatching { repository.getBlockedAccounts() }.onSuccess { _blockedAccounts.value = it }.onFailure { _error.value = it.message ?: "Blocked accounts could not load. Reference: ANDROID-BLOCKS" } }
    private fun work(block: suspend () -> Unit) = viewModelScope.launch { if (_working.value) return@launch; _working.value = true; _error.value = null; _message.value = null; try { block() } catch (error: Exception) { _error.value = error.message ?: "The action failed. Reference: ANDROID-SETTINGS" } finally { _working.value = false } }
}
