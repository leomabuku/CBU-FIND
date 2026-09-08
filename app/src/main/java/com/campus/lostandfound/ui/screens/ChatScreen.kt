package com.campus.lostandfound.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.campus.lostandfound.data.model.ChatMessage
import com.campus.lostandfound.data.model.ItemStatus
import com.campus.lostandfound.ui.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    currentUserId: String,
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val conversation by viewModel.conversation.collectAsStateWithLifecycle()
    val report by viewModel.report.collectAsStateWithLifecycle()
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val attachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
    val isUpdatingReport by viewModel.isUpdatingReport.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val notice by viewModel.notice.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    var confirmBlock by remember { mutableStateOf(false) }
    var confirmResolved by remember { mutableStateOf(false) }
    var reportMessageId by remember { mutableStateOf<String?>(null) }
    var reportDetails by remember { mutableStateOf("") }
    var editingMessageId by remember { mutableStateOf<String?>(null) }
    var messageToDelete by remember { mutableStateOf<ChatMessage?>(null) }
    val listState = rememberLazyListState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::attach)
    }
    val readOnly = conversation?.let { it.locked || it.closed } == true
    val canResolve = report?.userId == currentUserId && report?.status == ItemStatus.MATCHED && !readOnly

    LaunchedEffect(conversationId, currentUserId) { viewModel.load(conversationId, currentUserId) }
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            viewModel.markRead()
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(conversation?.displayName(currentUserId) ?: "Conversation")
                        conversation?.itemTitle?.takeIf { it.isNotBlank() }?.let {
                            Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = { IconButton(onClick = { confirmBlock = true }) { Icon(Icons.Default.Block, "Block account") } }
            )
        },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).imePadding().padding(10.dp)
            ) {
                if (canResolve) {
                    Button(
                        onClick = { confirmResolved = true },
                        enabled = !isUpdatingReport && !isSending,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        if (isUpdatingReport) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Text("Item returned — mark resolved", modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (report?.status == ItemStatus.RESOLVED) {
                    Text("✓ This report is resolved", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(6.dp))
                }
                notice?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(6.dp)) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(6.dp)) }
                if (editingMessageId != null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Editing message", color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                        TextButton(onClick = { editingMessageId = null; draft = "" }) { Text("Cancel") }
                    }
                }
                if (isUploading) {
                    Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Uploading attachment…", modifier = Modifier.padding(start = 10.dp))
                    }
                }
                attachment?.let { pending ->
                    Card(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            AttachmentIcon(pending.type.name)
                            Text(pending.name, modifier = Modifier.padding(horizontal = 8.dp).weight(1f), maxLines = 1)
                            IconButton(onClick = viewModel::removeAttachment) { Icon(Icons.Default.Close, "Remove attachment") }
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    IconButton(onClick = { picker.launch("*/*") }, enabled = editingMessageId == null && !readOnly && !isUploading && !isSending) {
                        Icon(Icons.Default.AttachFile, "Attach media")
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 4000) draft = it },
                        placeholder = { Text(if (conversation?.closed == true) "Match reopened — history only" else if (conversation?.locked == true) "Conversation locked" else "Write a message") },
                        enabled = !readOnly,
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f).heightIn(max = 140.dp),
                        maxLines = 5
                    )
                    IconButton(
                        onClick = {
                            val editId = editingMessageId
                            if (editId == null) viewModel.send(draft) { draft = "" }
                            else viewModel.editMessage(editId, draft) { draft = ""; editingMessageId = null }
                        },
                        enabled = !readOnly && !isUploading && !isSending && if (editingMessageId == null) (draft.isNotBlank() || attachment != null) else draft.isNotBlank()
                    ) {
                        if (isSending) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Filled.Send, "Send")
                    }
                }
            }
        }
    ) { padding ->
        if (syncState.isLoading) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (syncState.error != null && messages.isEmpty()) {
            ActionableFailure("Messages could not load", syncState.error!!, { viewModel.load(conversationId, currentUserId) }, Modifier.padding(padding))
        } else if (messages.isEmpty()) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Say hello and arrange a safe campus handover.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                syncState.error?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth().padding(8.dp)) } }
                items(messages, key = { it.id }) { message ->
                    val own = message.senderId == currentUserId
                    MessageBubble(
                        message = message,
                        own = own,
                        onReport = if (!own && !message.deleted) ({ reportMessageId = message.id }) else null,
                        onEdit = if (own && !message.deleted && message.text.isNotBlank() && !readOnly) ({ viewModel.removeAttachment(); editingMessageId = message.id; draft = message.text }) else null,
                        onDelete = if (own && !message.deleted) ({ messageToDelete = message }) else null
                    )
                }
            }
        }
    }

    if (confirmBlock) AlertDialog(onDismissRequest = { confirmBlock = false }, title = { Text("Block this account?") }, text = { Text("New claims and messages will be disabled in both directions. Existing history remains visible.") }, confirmButton = { Button(onClick = { confirmBlock = false; viewModel.blockOtherParticipant() }) { Text("Block") } }, dismissButton = { TextButton(onClick = { confirmBlock = false }) { Text("Cancel") } })
    if (confirmResolved) AlertDialog(
        onDismissRequest = { if (!isUpdatingReport) confirmResolved = false },
        title = { Text("Confirm item returned?") },
        text = { Text("This will mark the report resolved and notify the accepted claimant. You can reopen it later from the report details screen.") },
        confirmButton = { Button(enabled = !isUpdatingReport, onClick = { confirmResolved = false; viewModel.markReportResolved() }) { Text("Mark resolved") } },
        dismissButton = { TextButton(enabled = !isUpdatingReport, onClick = { confirmResolved = false }) { Text("Cancel") } }
    )
    if (reportMessageId != null) AlertDialog(
        onDismissRequest = { reportMessageId = null }, title = { Text("Report message") },
        text = { Column { Text("Moderators receive this message plus at most two messages before and after it."); OutlinedTextField(reportDetails, { reportDetails = it.take(1500) }, label = { Text("What happened?") }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) } },
        confirmButton = { Button(onClick = { viewModel.reportMessage(reportMessageId!!, reportDetails); reportMessageId = null; reportDetails = "" }) { Text("Submit report") } },
        dismissButton = { TextButton(onClick = { reportMessageId = null }) { Text("Cancel") } }
    )
    messageToDelete?.let { message ->
        AlertDialog(
            onDismissRequest = { messageToDelete = null },
            title = { Text("Delete message?") },
            text = { Text("Its text and attachment will be removed for everyone. This cannot be undone.") },
            confirmButton = { Button(onClick = { messageToDelete = null; if (editingMessageId == message.id) { editingMessageId = null; draft = "" }; viewModel.deleteMessage(message.id) }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { messageToDelete = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, own: Boolean, onReport: (() -> Unit)?, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth(), contentAlignment = if (own) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            modifier = Modifier
                .fillMaxWidth(.82f)
                .clip(RoundedCornerShape(22.dp))
                .background(if (own) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        ) {
            if (message.deleted) {
                Text("Message deleted", fontStyle = FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (message.mediaUrl.isNotBlank()) {
                if (message.mediaType == "IMAGE") {
                    AsyncImage(
                        model = message.mediaUrl,
                        contentDescription = message.mediaName.ifBlank { "Shared photo" },
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp).clip(RoundedCornerShape(16.dp)).clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(message.mediaUrl)))
                        }
                    )
                } else {
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(message.mediaUrl)))
                        }.padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AttachmentIcon(message.mediaType)
                        Column(Modifier.padding(start = 10.dp).weight(1f)) {
                            Text(message.mediaName.ifBlank { if (message.mediaType == "VIDEO") "Shared video" else "Attachment" }, fontWeight = FontWeight.Bold)
                            if (message.mediaSizeBytes > 0) Text(formatBytes(message.mediaSizeBytes), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            if (!message.deleted && message.text.isNotBlank()) Text(message.text, modifier = Modifier.padding(top = if (message.mediaUrl.isNotBlank()) 8.dp else 0.dp))
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.createdAt)) + if (message.editedAt > 0L && !message.deleted) " · edited" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                onEdit?.let { IconButton(onClick = it, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Edit, "Edit message", modifier = Modifier.size(16.dp)) } }
                onDelete?.let { IconButton(onClick = it, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Delete, "Delete message", modifier = Modifier.size(16.dp)) } }
                onReport?.let { IconButton(onClick = it, modifier = Modifier.size(30.dp)) { Icon(Icons.Default.Flag, "Report message", modifier = Modifier.size(16.dp)) } }
            }
        }
    }
}

@Composable
private fun AttachmentIcon(type: String) {
    Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.secondaryContainer, CircleShape), contentAlignment = Alignment.Center) {
        Icon(if (type == "VIDEO") Icons.Default.Movie else Icons.Default.Description, null, modifier = Modifier.size(20.dp))
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
    bytes >= 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f)
    else -> "$bytes B"
}
