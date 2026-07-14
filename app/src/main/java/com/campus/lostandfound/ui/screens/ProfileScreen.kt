package com.campus.lostandfound.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campus.lostandfound.data.model.User
import com.campus.lostandfound.ui.viewmodel.ProfileViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: User,
    viewModel: ProfileViewModel,
    onLogout: () -> Unit,
    onNavigateBack: () -> Unit,
    onProfileUpdated: () -> Unit,
    onItemClick: (String) -> Unit
) {
    var showEdit by remember { mutableStateOf(false) }
    val userItems by viewModel.userItems.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    LaunchedEffect(user.id) { viewModel.loadUserItems(user.id) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Your profile") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = { TextButton(onClick = onLogout) { Text("Sign out", color = MaterialTheme.colorScheme.error) } }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        Modifier.size(82.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (user.photoUrl.isNotBlank()) {
                            AsyncImage(
                                model = user.photoUrl,
                                contentDescription = "${user.name} profile photo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().clip(CircleShape)
                            )
                        } else {
                            Text(
                                user.name.trim().take(1).uppercase().ifBlank { "C" },
                                style = MaterialTheme.typography.headlineLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    Text(user.name.ifBlank { "Campus member" }, style = MaterialTheme.typography.headlineMedium)
                    Text(user.email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showEdit = true }) { Text("Edit profile") }
                    }
                }
            }
            item {
                ProfileField("Student ID", user.studentId.ifBlank { "Not provided" })
                ProfileField("Programme", user.programme.ifBlank { "Not provided" })
                ProfileField("Year of study", user.yearOfStudy.ifBlank { "Not provided" })
                ProfileField("Contact", user.phone.ifBlank { "Not provided" })
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                Text("My reports", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
            }
            if (userItems.isEmpty()) {
                item { Text("You have not posted a report yet.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(userItems, key = { it.id }) { item -> ItemCard(item) { onItemClick(item.id) } }
            }
        }
    }

    if (showEdit) {
        EditProfileDialog(
            user = user,
            viewModel = viewModel,
            onDismiss = { showEdit = false },
            onSaved = { showEdit = false; onProfileUpdated() }
        )
    }
}

@Composable
private fun ProfileField(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EditProfileDialog(
    user: User,
    viewModel: ProfileViewModel,
    onDismiss: () -> Unit,
    onSaved: () -> Unit
) {
    var name by remember { mutableStateOf(user.name) }
    var programme by remember { mutableStateOf(user.programme) }
    var year by remember { mutableStateOf(user.yearOfStudy) }
    var phone by remember { mutableStateOf(user.phone) }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    val isSaving by viewModel.isSaving.collectAsStateWithLifecycle()
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        photoUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Complete your profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { photoPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (photoUri == null) "Choose profile photo" else "Profile photo selected")
                }
                OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true)
                OutlinedTextField(programme, { programme = it }, label = { Text("Programme") }, singleLine = true)
                OutlinedTextField(year, { year = it }, label = { Text("Year of study") }, singleLine = true)
                OutlinedTextField(phone, { phone = it }, label = { Text("Contact number") }, singleLine = true)
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && !isSaving,
                onClick = {
                    viewModel.updateProfile(
                        user.copy(
                            name = name.trim(),
                            programme = programme.trim(),
                            yearOfStudy = year.trim(),
                            phone = phone.trim()
                        ),
                        photoUri,
                        onSaved
                    )
                }
            ) { Text(if (isSaving) "Saving…" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
