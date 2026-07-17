package com.campus.lostandfound.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campus.lostandfound.data.model.ItemStatus
import com.campus.lostandfound.data.model.ItemType
import com.campus.lostandfound.ui.viewmodel.ItemDetailsViewModel
import coil.compose.AsyncImage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailsScreen(
    itemId: String,
    currentUserId: String,
    viewModel: ItemDetailsViewModel,
    onNavigateBack: () -> Unit,
    onOpenConversation: (String) -> Unit
) {
    var confirmResolved by remember { mutableStateOf(false) }
    val item by viewModel.item.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val isStartingChat by viewModel.isStartingChat.collectAsStateWithLifecycle()
    LaunchedEffect(itemId) { viewModel.loadItem(itemId) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Report details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            isLoading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            item == null -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(error ?: "Report not found", color = MaterialTheme.colorScheme.error)
            }
            else -> {
                val report = item!!
                Column(
                    modifier = Modifier
                        .padding(padding)
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SuggestionChip(
                            onClick = {},
                            label = { Text(if (report.type == ItemType.LOST) "LOST" else "FOUND") },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = if (report.type == ItemType.LOST)
                                    MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
                            )
                        )
                        SuggestionChip(onClick = {}, label = { Text(report.category) })
                    }
                    Text(report.title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(vertical = 14.dp))
                    val imageUrls = report.imageUrls.ifEmpty { listOfNotNull(report.imageUri) }
                    if (imageUrls.isNotEmpty()) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            items(imageUrls) { imageUrl ->
                                AsyncImage(
                                    model = imageUrl,
                                    contentDescription = "Photo of ${report.title}",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.size(250.dp, 190.dp).clip(RoundedCornerShape(18.dp))
                                )
                            }
                        }
                    }
                    if (report.status == ItemStatus.RESOLVED) {
                        Card(
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                        ) {
                            Text(
                                "This item has been returned to its owner.",
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                    DetailBlock("Location", report.location)
                    DetailBlock(
                        if (report.type == ItemType.LOST) "Reported lost" else "Reported found",
                        SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date(report.date))
                    )
                    DetailBlock("Description", report.description.ifBlank { "No description provided." })
                    DetailBlock("Contact", report.contactInfo.ifBlank { "Contact details were not provided." })
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }

                    if (report.userId == currentUserId && report.status == ItemStatus.ACTIVE) {
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { confirmResolved = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Mark item as returned")
                        }
                    } else if (report.userId != currentUserId && report.status == ItemStatus.ACTIVE) {
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.startConversation(currentUserId, onOpenConversation) },
                            enabled = !isStartingChat && currentUserId.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isStartingChat) {
                                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.size(8.dp))
                            } else {
                                Icon(Icons.Default.Email, null)
                                Spacer(Modifier.size(8.dp))
                            }
                            Text(if (isStartingChat) "Opening chat…" else "Message report owner")
                        }
                    }
                }
            }
        }
    }

    if (confirmResolved) {
        AlertDialog(
            onDismissRequest = { confirmResolved = false },
            title = { Text("Item returned?") },
            text = { Text("This closes the report and tells the campus community that the item is back with its owner.") },
            confirmButton = {
                Button(onClick = { confirmResolved = false; viewModel.markAsResolved() }) { Text("Yes, mark returned") }
            },
            dismissButton = { TextButton(onClick = { confirmResolved = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DetailBlock(label: String, value: String) {
    Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))
}
