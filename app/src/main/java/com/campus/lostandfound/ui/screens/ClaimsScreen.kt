package com.campus.lostandfound.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campus.lostandfound.data.model.Claim
import com.campus.lostandfound.data.model.ClaimStatus
import com.campus.lostandfound.ui.viewmodel.ClaimsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClaimsScreen(currentUserId: String, viewModel: ClaimsViewModel, onBack: () -> Unit, onOpenConversation: (String) -> Unit) {
    val claims by viewModel.claims.collectAsStateWithLifecycle()
    val sync by viewModel.syncState.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val workingId by viewModel.workingId.collectAsStateWithLifecycle()
    LaunchedEffect(currentUserId) { viewModel.load(currentUserId) }
    Scaffold(topBar = { TopAppBar(title = { Column { Text("Claims"); Text("Accept before messaging", style = MaterialTheme.typography.labelMedium) } }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        when {
            sync.isLoading -> Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            sync.error != null && claims.isEmpty() -> ActionableFailure("Claims could not load", sync.error!!, onBack, Modifier.padding(padding))
            claims.isEmpty() -> EmptyFeatureState("No claims yet", "Claims you submit and responses to your reports appear here.", Modifier.padding(padding))
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                sync.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
                message?.let { item { Text(it, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) } }
                error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
                items(claims, key = { it.id }) { claim -> ClaimRow(claim, currentUserId, workingId == claim.id, viewModel, onOpenConversation) }
            }
        }
    }
}

@Composable private fun ClaimRow(claim: Claim, uid: String, working: Boolean, viewModel: ClaimsViewModel, onOpenConversation: (String) -> Unit) {
    val incoming = claim.itemOwnerId == uid
    Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(if (incoming) "Claim on your report" else "Your claim", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(claim.status.name, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold) }
            Text(claim.kind.name.replace('_', ' '), style = MaterialTheme.typography.labelMedium)
            Text(claim.note.ifBlank { "No additional note." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (claim.status == ClaimStatus.PENDING) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (incoming) { Button(enabled = !working, onClick = { viewModel.action(claim.id, "ACCEPT", onOpenConversation) }) { Text("Accept") }; TextButton(enabled = !working, onClick = { viewModel.action(claim.id, "REJECT") }) { Text("Reject") } }
                else TextButton(enabled = !working, onClick = { viewModel.action(claim.id, "CANCEL") }) { Text("Cancel claim") }
            }
            if (claim.status == ClaimStatus.ACCEPTED && claim.conversationId.isNotBlank()) Button(onClick = { onOpenConversation(claim.conversationId) }) { Text("Open conversation") }
        }
    }
}

@Composable fun ActionableFailure(title: String, message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) { Column(modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(title, style = MaterialTheme.typography.titleLarge); Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 12.dp)); Button(onClick = onRetry) { Text("Retry") } } }
@Composable fun EmptyFeatureState(title: String, message: String, modifier: Modifier = Modifier) { Column(modifier.fillMaxSize().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Text(title, style = MaterialTheme.typography.titleLarge); Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp)) } }
