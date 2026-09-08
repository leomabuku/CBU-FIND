package com.campus.lostandfound.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.lostandfound.data.model.Item
import com.campus.lostandfound.data.model.ItemType
import com.campus.lostandfound.data.model.ItemStatus
import com.campus.lostandfound.data.repository.AppRepository
import com.campus.lostandfound.data.repository.SyncState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class HomeViewModel(private val repository: AppRepository) : ViewModel() {

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _selectedTab = MutableStateFlow(ItemType.LOST)
    val selectedTab: StateFlow<ItemType> = _selectedTab

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory

    private val _showResolved = MutableStateFlow(false)
    val showResolved: StateFlow<Boolean> = _showResolved

    private val _role = MutableStateFlow("USER")
    val role: StateFlow<String> = _role.asStateFlow()

    private val refreshNonce = MutableStateFlow(0)
    private val sourceItems = combine(_searchQuery, _selectedTab, refreshNonce) { query, type, _ -> query to type }
        .flatMapLatest { (query, type) ->
        if (query.isBlank()) {
            repository.getItemsByType(type) { _syncState.value = it }
        } else {
            repository.searchItems(query, type) { _syncState.value = it }
        }
    }

    val items: StateFlow<List<Item>> = combine(
        sourceItems,
        _selectedCategory,
        _showResolved
    ) { items, category, showResolved ->
        items.filter { item ->
            (category == null || item.category == category) &&
                (showResolved || item.status == ItemStatus.ACTIVE)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
    }

    fun onTabSelected(type: ItemType) {
        _selectedTab.value = type
    }

    fun onCategorySelected(category: String?) {
        _selectedCategory.value = category
    }

    fun onShowResolvedChange(show: Boolean) {
        _showResolved.value = show
    }

    fun loadRole(userId: String) {
        if (userId.isBlank()) { _role.value = "USER"; return }
        viewModelScope.launch { _role.value = runCatching { repository.getRole(userId) }.getOrDefault("USER") }
    }

    fun retry() { refreshNonce.value += 1 }
}
