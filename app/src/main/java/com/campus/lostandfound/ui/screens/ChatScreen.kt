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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.campus.lostandfound.data.model.ChatMessage
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
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val attachment by viewModel.pendingAttachment.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val isUploading by viewModel.isUploading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var draft by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let(viewModel::attach)
    }

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
                }
            )
        },
        bottomBar = {
            Column(
                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).imePadding().padding(10.dp)
            ) {
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(6.dp)) }
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
                    IconButton(onClick = { picker.launch("*/*") }, enabled = !isUploading && !isSending) {
                        Icon(Icons.Default.AttachFile, "Attach media")
                    }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { if (it.length <= 4000) draft = it },
                        placeholder = { Text("Write a message") },
                        shape = RoundedCornerShape(24.dp),
                        modifier = Modifier.weight(1f).heightIn(max = 140.dp),
                        maxLines = 5
                    )
                    IconButton(
                        onClick = { viewModel.send(draft) { draft = "" } },
                        enabled = !isUploading && !isSending && (draft.isNotBlank() || attachment != null)
                    ) {
                        if (isSending) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        else Icon(Icons.AutoMirrored.Filled.Send, "Send")
                    }
                }
            }
        }
    ) { padding ->
        if (messages.isEmpty()) {
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
                items(messages, key = { it.id }) { message ->
                    MessageBubble(message, message.senderId == currentUserId)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, own: Boolean) {
    val context = LocalContext.current
    Box(Modifier.fillMaxWidth(), contentAlignment = if (own) Alignment.CenterEnd else Alignment.CenterStart) {
        Column(
            modifier = Modifier
                .fillMaxWidth(.82f)
                .clip(RoundedCornerShape(22.dp))
                .background(if (own) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp)
        ) {
            if (message.mediaUrl.isNotBlank()) {
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
            if (message.text.isNotBlank()) Text(message.text, modifier = Modifier.padding(top = if (message.mediaUrl.isNotBlank()) 8.dp else 0.dp))
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.createdAt)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End).padding(top = 4.dp)
            )
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
