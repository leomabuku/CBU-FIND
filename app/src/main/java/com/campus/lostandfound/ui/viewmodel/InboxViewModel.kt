package com.campus.lostandfound.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.lostandfound.data.model.Conversation
import com.campus.lostandfound.data.repository.AppRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(private val repository: AppRepository) : ViewModel() {
    private val currentUserId = MutableStateFlow("")

    val conversations: StateFlow<List<Conversation>> = currentUserId
        .flatMapLatest { userId ->
            if (userId.isBlank()) flowOf(emptyList()) else repository.getConversations(userId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun load(userId: String) {
        currentUserId.value = userId
    }
}
