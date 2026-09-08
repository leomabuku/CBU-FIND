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
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    currentUserId: String,
    onCreateListing: () -> Unit,
    onItemClick: (String) -> Unit,
    onProfileClick: () -> Unit,
    onInboxClick: () -> Unit,
    onClaimsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onModerationClick: () -> Unit
) {
    val reports by viewModel.items.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val role by viewModel.role.collectAsStateWithLifecycle()
    LaunchedEffect(currentUserId) { viewModel.loadRole(currentUserId) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Surface(color = MaterialTheme.colorScheme.surface) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(R.drawable.cbu_find_logo),
                        contentDescription = "CBU Find",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp))
                    )
                    Column(Modifier.padding(start = 10.dp).weight(1f)) {
                        Text("CBU Find", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold)
                        Text("Lost it? Let's find it.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (role == "MODERATOR" || role == "ADMIN") IconButton(onClick = onModerationClick) { Icon(Icons.Default.AdminPanelSettings, "Moderation") }
                    IconButton(onClick = onProfileClick) {
                        Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.Person, "Profile", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(selected = true, onClick = {}, icon = { Icon(Icons.Default.Home, null) }, label = { Text("Home") })
                NavigationBarItem(selected = false, onClick = onClaimsClick, icon = { Icon(Icons.Default.AssignmentTurnedIn, null) }, label = { Text("Claims") })
                NavigationBarItem(selected = false, onClick = onInboxClick, icon = { Icon(Icons.Default.Email, null) }, label = { Text("Messages") })
                NavigationBarItem(selected = false, onClick = onSettingsClick, icon = { Icon(Icons.Default.Settings, null) }, label = { Text("Settings") })
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateListing,
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text("Create report", fontWeight = FontWeight.Bold) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 20.dp)) {
                    Text("Good evening", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyLarge)
                    Text("Campus reports", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                }
            }

            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = viewModel::onSearchQueryChange,
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    placeholder = { Text("Search items, places or categories") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp)
                )
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    FilterChip(selected = selectedTab == ItemType.LOST, onClick = { viewModel.onTabSelected(ItemType.LOST) }, label = { Text("LOST") }, modifier = Modifier.weight(1f))
                    FilterChip(selected = selectedTab == ItemType.FOUND, onClick = { viewModel.onTabSelected(ItemType.FOUND) }, label = { Text("FOUND") }, modifier = Modifier.weight(1f))
                }
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

            item { Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp)) { Text("Recent reports", style = MaterialTheme.typography.titleLarge); Text(if (selectedTab == ItemType.LOST) "Items the community is looking for" else "Items waiting to be reunited", color = MaterialTheme.colorScheme.onSurfaceVariant) } }

            if (reports.isEmpty()) {
                item {
                    when {
                        syncState.isLoading -> Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator() }
                        syncState.error != null -> ActionableFailure("Reports could not load", syncState.error!!, viewModel::retry, Modifier.fillMaxWidth().height(260.dp))
                        else -> EmptyReportsState(searchQuery.isNotBlank() || selectedCategory != null, onCreateListing)
                    }
                }
            } else {
                items(reports, key = { it.id }) { report ->
                    CompactItemRow(report) { onItemClick(report.id) }
                }
            }
        }
    }
}

@Composable
private fun CompactItemRow(item: Item, onClick: () -> Unit) {
    val imageUrl = item.imageUrls.firstOrNull() ?: item.imageUri
    Surface(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick), color = MaterialTheme.colorScheme.surface) {
        Column {
            Row(Modifier.padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                if (!imageUrl.isNullOrBlank()) {
                    AsyncImage(model = imageUrl, contentDescription = "Photo of ${item.title}", contentScale = ContentScale.Crop, modifier = Modifier.size(76.dp).clip(RoundedCornerShape(16.dp)))
                } else {
                    Box(Modifier.size(76.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.primary) }
                }
                Column(Modifier.padding(start = 13.dp).weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (item.type == ItemType.LOST) "LOST" else "FOUND", color = if (item.type == ItemType.LOST) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.weight(1f))
                        Text(SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(item.date)), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.LocationOn, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Text(item.location, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 3.dp))
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = .45f))
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
