package com.campus.lostandfound.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campus.lostandfound.data.model.ModerationCase
import com.campus.lostandfound.ui.viewmodel.ModerationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModerationScreen(currentUserId: String, viewModel: ModerationViewModel, onBack: () -> Unit) {
    val role by viewModel.role.collectAsStateWithLifecycle(); val cases by viewModel.cases.collectAsStateWithLifecycle(); val sync by viewModel.syncState.collectAsStateWithLifecycle(); val error by viewModel.error.collectAsStateWithLifecycle()
    var targetUid by remember { mutableStateOf("") }
    LaunchedEffect(currentUserId) { viewModel.load(currentUserId) }
    Scaffold(topBar = { TopAppBar(title = { Text("Moderation") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        if (role == "USER") ActionableFailure("Moderator access required", error ?: "This area is limited to moderators and administrators.", onBack, Modifier.padding(padding))
        else LazyColumn(Modifier.padding(padding).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("$role tools", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(16.dp)) }
            if (role == "ADMIN") item { Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) { Column(Modifier.padding(14.dp)) { Text("Role management", fontWeight = FontWeight.Bold); OutlinedTextField(targetUid, { targetUid = it }, label = { Text("Firebase user UID") }, modifier = Modifier.fillMaxWidth()); Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { Button(enabled = targetUid.isNotBlank(), onClick = { viewModel.assignRole(targetUid, "MODERATOR") }) { Text("Moderator") }; TextButton(enabled = targetUid.isNotBlank(), onClick = { viewModel.assignRole(targetUid, "ADMIN") }) { Text("Admin") }; TextButton(enabled = targetUid.isNotBlank(), onClick = { viewModel.assignRole(targetUid, "USER") }) { Text("User") } }; Text("The Worker prevents removing the last administrator.", style = MaterialTheme.typography.labelMedium) } } }
            error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            sync.error?.let { item { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(16.dp)) } }
            if (cases.isEmpty() && !sync.isLoading) item { EmptyFeatureState("No moderation cases", "New reports appear with only the target and allowed adjacent context.") }
            items(cases, key = { it.id }) { record -> ModerationCaseCard(record, viewModel) }
        }
    }
}

@Composable private fun ModerationCaseCard(record: ModerationCase, viewModel: ModerationViewModel) {
    var userTargetUid by remember(record.id) { mutableStateOf(record.subjectUserId) }
    Card(Modifier.fillMaxWidth().padding(horizontal = 14.dp), shape = RoundedCornerShape(18.dp)) { Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row { Text(record.reason, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text(record.status, color = MaterialTheme.colorScheme.primary) }
        Text(record.details.ifBlank { "No additional details." }, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (record.context.isNotEmpty()) { Text("Reported message with up to two messages before and after", style = MaterialTheme.typography.labelMedium); record.context.forEach { Text("${it.senderId}: ${it.text.ifBlank { "[${it.mediaType.ifBlank { "attachment" }}]" }}", style = MaterialTheme.typography.bodySmall) } }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TextButton(onClick = { viewModel.action(record, "DISMISS") }) { Text("Dismiss") }
            if (record.targetType == "REPORT") { TextButton(onClick = { viewModel.action(record, "REMOVE_REPORT") }) { Text("Remove") }; TextButton(onClick = { viewModel.action(record, "RESTORE_REPORT") }) { Text("Restore") } }
            if (record.targetType == "MESSAGE") { TextButton(onClick = { viewModel.action(record, "LOCK_CONVERSATION", record.conversationId) }) { Text("Lock chat") }; TextButton(onClick = { viewModel.action(record, "UNLOCK_CONVERSATION", record.conversationId) }) { Text("Unlock") } }
        }
        OutlinedTextField(userTargetUid, { userTargetUid = it.trim() }, label = { Text("Target user UID for account action") }, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { TextButton(enabled = userTargetUid.isNotBlank(), onClick = { viewModel.action(record, "SUSPEND_USER", userTargetUid) }) { Text("Suspend user") }; TextButton(enabled = userTargetUid.isNotBlank(), onClick = { viewModel.action(record, "REACTIVATE_USER", userTargetUid) }) { Text("Reactivate") } }
    } }
}
