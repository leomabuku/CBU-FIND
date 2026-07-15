package com.campus.lostandfound.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.campus.lostandfound.R
import com.campus.lostandfound.data.model.Item
import com.campus.lostandfound.data.model.ItemCategories
import com.campus.lostandfound.data.model.ItemStatus
import com.campus.lostandfound.data.model.ItemType
import com.campus.lostandfound.data.repository.SyncState
import com.campus.lostandfound.ui.viewmodel.HomeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeDashboardScreen(
    viewModel: HomeViewModel,
    onCreateListing: () -> Unit,
    onItemClick: (String) -> Unit,
    onProfileClick: () -> Unit
) {
    val reports by viewModel.items.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth()
                        .background(MaterialTheme.colorScheme.onBackground)
                        .padding(horizontal = 18.dp, vertical = 14.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(R.drawable.cbu_find_logo),
                            contentDescription = "CBU Find",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.size(58.dp).clip(RoundedCornerShape(14.dp))
                        )
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text("CBU Find", color = MaterialTheme.colorScheme.background, style = MaterialTheme.typography.titleLarge)
                            Text("Lost it? Let's find it.", color = MaterialTheme.colorScheme.background.copy(alpha = .7f))
                        }
                        IconButton(onClick = onProfileClick) {
                            Icon(Icons.Default.Person, "Profile", tint = MaterialTheme.colorScheme.background)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Text("Good to see you.", color = MaterialTheme.colorScheme.background, style = MaterialTheme.typography.headlineMedium)
                    Text(
                        "Let's get items back where they belong.",
                        color = MaterialTheme.colorScheme.background.copy(alpha = .72f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        HeroAction("I lost\nsomething", ItemType.LOST, selectedTab == ItemType.LOST) {
                            viewModel.onTabSelected(ItemType.LOST)
                            onCreateListing()
                        }
                        HeroAction("I found\nsomething", ItemType.FOUND, selectedTab == ItemType.FOUND) {
                            viewModel.onTabSelected(ItemType.FOUND)
                            onCreateListing()
                        }
                    }
                }
            }

            item {
                ReturnedBanner(reports.count { it.status == ItemStatus.RESOLVED })
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search items, places or categories") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp)
                )
            }

            if (!syncState.isOnline) item { SyncBanner(syncState) }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { viewModel.onCategorySelected(null) },
                            label = { Text("All") }
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
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Recent reports", style = MaterialTheme.typography.titleLarge)
                        Text(
                            if (selectedTab == ItemType.LOST) "Items the community is looking for" else "Items waiting to be reunited",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(if (selectedTab == ItemType.LOST) "LOST" else "FOUND", color = if (selectedTab == ItemType.LOST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.Bold)
                }
            }

            if (reports.isEmpty()) {
                item { EmptyReportsState(searchQuery.isNotBlank() || selectedCategory != null, onCreateListing) }
            } else {
                items(reports, key = { it.id }) { report ->
                    ItemCard(report) { onItemClick(report.id) }
                }
            }
        }
    }
}

@Composable
private fun RowScope.HeroAction(text: String, type: ItemType, selected: Boolean, onClick: () -> Unit) {
    val color = if (type == ItemType.LOST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    Card(
        modifier = Modifier.weight(1f).height(116.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(if (selected) 8.dp else 2.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(if (type == ItemType.LOST) Icons.Default.Search else Icons.Default.CheckCircle, null, tint = androidx.compose.ui.graphics.Color.White)
            Text(text, color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ReturnedBanner(returnedCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(22.dp)
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).background(MaterialTheme.colorScheme.secondary, CircleShape), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CheckCircle, null, tint = androidx.compose.ui.graphics.Color.White)
            }
            Column(Modifier.padding(start = 12.dp)) {
                Text("Every return is a win", fontWeight = FontWeight.Bold)
                Text(if (returnedCount > 0) "$returnedCount ${if (returnedCount == 1) "item" else "items"} reunited in this view." else "Your report could help make the next match.")
            }
        }
    }
}

@Composable
private fun EmptyReportsState(filtered: Boolean, onCreateListing: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (filtered) "No matching reports" else "Nothing here yet", style = MaterialTheme.typography.titleLarge)
        Text(if (filtered) "Try another search or category." else "Start the next campus success story.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        Card(onClick = onCreateListing, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                Icon(Icons.Default.Add, null, tint = androidx.compose.ui.graphics.Color.White)
                Text("Report an item", color = androidx.compose.ui.graphics.Color.White, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
fun ItemCard(item: Item, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 7.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(3.dp)
    ) {
        val imageUrl = item.imageUrls.firstOrNull() ?: item.imageUri
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Photo of ${item.title}",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(190.dp)
            )
        }
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    when { item.status == ItemStatus.RESOLVED -> "RETURNED"; item.type == ItemType.LOST -> "LOST"; else -> "FOUND" },
                    color = when { item.status == ItemStatus.RESOLVED -> MaterialTheme.colorScheme.tertiary; item.type == ItemType.LOST -> MaterialTheme.colorScheme.primary; else -> MaterialTheme.colorScheme.secondary },
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(Modifier.weight(1f))
                Text(SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(item.date)), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
            Text(item.category, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Text(item.location, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
private fun SyncBanner(syncState: SyncState) {
    val message = when {
        syncState.error != null -> syncState.error
        syncState.hasPendingWrites -> "Saving your latest changes…"
        else -> "Showing saved reports. Connect for the latest updates."
    }
    Text(message.orEmpty(), modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(14.dp))
}
