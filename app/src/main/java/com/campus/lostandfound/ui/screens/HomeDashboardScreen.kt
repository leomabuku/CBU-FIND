package com.campus.lostandfound.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campus.lostandfound.data.model.Item
import com.campus.lostandfound.data.model.ItemCategories
import com.campus.lostandfound.data.model.ItemStatus
import com.campus.lostandfound.data.model.ItemType
import com.campus.lostandfound.ui.viewmodel.HomeViewModel
import com.campus.lostandfound.data.repository.SyncState
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeDashboardScreen(
    viewModel: HomeViewModel,
    onCreateListing: () -> Unit,
    onItemClick: (String) -> Unit
) {
    val reports by viewModel.items.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val showResolved by viewModel.showResolved.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onCreateListing) {
                Icon(Icons.Default.Add, contentDescription = "Create report")
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Text("Reconnect people with their things.", style = MaterialTheme.typography.titleLarge)
                Text(
                    "Search recent reports from campus and nearby areas.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Search name, place or category") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp)
                )
            }

            TabRow(selectedTabIndex = if (selectedTab == ItemType.LOST) 0 else 1) {
                Tab(
                    selected = selectedTab == ItemType.LOST,
                    onClick = { viewModel.onTabSelected(ItemType.LOST) },
                    text = { Text("Lost items") }
                )
                Tab(
                    selected = selectedTab == ItemType.FOUND,
                    onClick = { viewModel.onTabSelected(ItemType.FOUND) },
                    text = { Text("Found items") }
                )
            }

            if (!syncState.isOnline) {
                SyncBanner(syncState)
            }

            LazyRow(
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { viewModel.onCategorySelected(null) },
                        label = { Text("All categories") }
                    )
                }
                items(ItemCategories.all) { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { viewModel.onCategorySelected(if (selectedCategory == category) null else category) },
                        label = { Text(category) }
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${reports.size} ${if (reports.size == 1) "report" else "reports"}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                Text("Show returned", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = showResolved, onCheckedChange = viewModel::onShowResolvedChange)
            }

            if (reports.isEmpty()) {
                EmptyReportsState(selectedTab, searchQuery.isNotBlank() || selectedCategory != null, onCreateListing)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(reports, key = { it.id }) { report ->
                        ItemCard(item = report, onClick = { onItemClick(report.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyReportsState(type: ItemType, filtered: Boolean, onCreateListing: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (filtered) "No matching reports" else "Nothing reported yet", style = MaterialTheme.typography.titleLarge)
            Text(
                if (filtered) "Try another search or category."
                else "Be the first to report a ${type.name.lowercase()} item.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 10.dp)
            )
            FilledTonalButton(onClick = onCreateListing) { Text("Create a report") }
        }
    }
}

@Composable
fun ItemCard(item: Item, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            val imageUrl = item.imageUrls.firstOrNull() ?: item.imageUri
            if (!imageUrl.isNullOrBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Photo of ${item.title}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                StatusPill(item)
                Spacer(Modifier.weight(1f))
                Text(
                    SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(item.date)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                item.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(item.category, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            Text(
                item.description.ifBlank { "No additional description provided." },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text("Location · ${item.location}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SyncBanner(syncState: SyncState) {
    val message = when {
        syncState.error != null -> syncState.error
        syncState.hasPendingWrites -> "Waiting for Firebase to confirm changes…"
        else -> "Showing saved reports. Connect to Firestore for the latest updates."
    }
    Text(
        message.orEmpty(),
        color = MaterialTheme.colorScheme.onErrorContainer,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    )
}

@Composable
private fun StatusPill(item: Item) {
    val text = when {
        item.status == ItemStatus.RESOLVED -> "RETURNED"
        item.type == ItemType.LOST -> "LOST"
        else -> "FOUND"
    }
    val color = when {
        item.status == ItemStatus.RESOLVED -> MaterialTheme.colorScheme.onSurfaceVariant
        item.type == ItemType.LOST -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.tertiary
    }
    Text(text, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
}
