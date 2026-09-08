package com.campus.lostandfound.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
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
import coil.compose.AsyncImage
import com.campus.lostandfound.data.model.ItemStatus
import com.campus.lostandfound.data.model.ItemType
import com.campus.lostandfound.ui.viewmodel.ItemDetailsViewModel
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
    onClaimSubmitted: () -> Unit
) {
    var confirmStatus by remember { mutableStateOf(false) }
    var showClaim by remember { mutableStateOf(false) }
    var claimNote by remember { mutableStateOf("") }
    val item by viewModel.item.collectAsStateWithLifecycle()
    val loading by viewModel.isLoading.collectAsStateWithLifecycle()
    val working by viewModel.isWorking.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val contact by viewModel.contact.collectAsStateWithLifecycle()
    LaunchedEffect(itemId) { viewModel.loadItem(itemId) }

    Scaffold(topBar = { TopAppBar(title = { Text("Report details") }, navigationIcon = { IconButton(onClick = onNavigateBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        when {
            loading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            item == null -> ErrorState(error ?: "This report is no longer available.", Modifier.padding(padding), onNavigateBack)
            else -> {
                val report = item!!
                Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SuggestionChip(onClick = {}, label = { Text(report.type.name) }); SuggestionChip(onClick = {}, label = { Text(report.status.name) }) }
                    Text(report.title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(vertical = 14.dp))
                    val imageUrls = report.media.map { it.secureUrl }.filter { it.isNotBlank() }.ifEmpty { report.imageUrls.ifEmpty { listOfNotNull(report.imageUri) } }
                    if (imageUrls.isNotEmpty()) LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 16.dp)) { items(imageUrls) { imageUrl -> AsyncImage(imageUrl, "Photo of ${report.title}", contentScale = ContentScale.Crop, modifier = Modifier.size(250.dp, 190.dp).clip(RoundedCornerShape(18.dp))) } }
                    if (report.status == ItemStatus.MATCHED) StatusCard("A claim has been accepted. The participants can now arrange a private handover.")
                    if (report.status == ItemStatus.RESOLVED) StatusCard("This report has been marked resolved.")
                    DetailBlock("Category", report.category)
                    DetailBlock("Location", report.location)
                    DetailBlock(if (report.type == ItemType.LOST) "Reported lost" else "Reported found", SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.getDefault()).format(Date(report.date)))
                    DetailBlock("Description", report.description.ifBlank { "No description provided." })
                    Text("Private contact details are visible only to the report owner and an accepted claimant.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 16.dp))
                    if (report.userId == currentUserId || report.status == ItemStatus.MATCHED) {
                        TextButton(onClick = viewModel::loadContact, enabled = !working) { Text("View private contact details") }
                    }
                    contact?.let { StatusCard("Private contact: $it") }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
                    when {
                        report.userId == currentUserId && report.status != ItemStatus.REMOVED -> Button(onClick = { confirmStatus = true }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text(if (report.status == ItemStatus.RESOLVED) "Reopen report" else "✓ Item returned — mark resolved") }
                        report.userId != currentUserId && report.status == ItemStatus.ACTIVE -> Button(onClick = { showClaim = true }, enabled = !working && currentUserId.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Text(if (report.type == ItemType.LOST) "I found this" else "This is mine") }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }

    if (confirmStatus) {
        val reopening = item?.status == ItemStatus.RESOLVED
        AlertDialog(
            onDismissRequest = { if (!working) confirmStatus = false },
            title = { Text(if (reopening) "Reopen this report?" else "Confirm item returned?") },
            text = { Text(if (reopening) "The accepted claim will be closed and this report will accept new claims again." else "This will mark the report resolved and notify the accepted claimant.") },
            confirmButton = { Button(enabled = !working, onClick = { confirmStatus = false; viewModel.changeResolvedState() }) { Text(if (reopening) "Reopen report" else "Mark resolved") } },
            dismissButton = { TextButton(enabled = !working, onClick = { confirmStatus = false }) { Text("Cancel") } }
        )
    }
    if (showClaim) AlertDialog(
        onDismissRequest = { if (!working) showClaim = false },
        title = { Text("Submit a claim") },
        text = { Column { Text("Add a detail that helps the owner assess your claim. Messaging opens only after acceptance."); OutlinedTextField(claimNote, { claimNote = it.take(1000) }, label = { Text("Optional note") }, modifier = Modifier.fillMaxWidth().padding(top = 10.dp)) } },
        confirmButton = { Button(enabled = !working, onClick = { viewModel.submitClaim(claimNote) { showClaim = false; onClaimSubmitted() } }) { if (working) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Submit") } },
        dismissButton = { TextButton(enabled = !working, onClick = { showClaim = false }) { Text("Cancel") } }
    )
}

@Composable private fun StatusCard(message: String) { Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 14.dp)) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.CheckCircle, null); Text(message, modifier = Modifier.padding(start = 10.dp), fontWeight = FontWeight.SemiBold) } } }
@Composable private fun DetailBlock(label: String, value: String) { Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)) }
@Composable private fun ErrorState(message: String, modifier: Modifier, onBack: () -> Unit) { Column(modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text("Report could not load", style = MaterialTheme.typography.titleLarge); Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)); Button(onClick = onBack) { Text("Go back") } } }
