package com.campus.lostandfound.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campus.lostandfound.data.model.ItemCategories
import com.campus.lostandfound.data.model.ItemType
import com.campus.lostandfound.ui.viewmodel.CreateItemViewModel
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateListingScreen(
    viewModel: CreateItemViewModel,
    userId: String,
    onNavigateBack: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var contactInfo by remember { mutableStateOf("") }
    var itemType by remember { mutableStateOf(ItemType.LOST) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var validationError by remember { mutableStateOf<String?>(null) }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val isSubmitting by viewModel.isSubmitting.collectAsStateWithLifecycle()
    val submitError by viewModel.error.collectAsStateWithLifecycle()
    val progressMessage by viewModel.progressMessage.collectAsStateWithLifecycle()
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        imageUris = (imageUris + uris).distinct().take(3)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create report") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text("What happened?", style = MaterialTheme.typography.headlineMedium)
            Text(
                "Share clear details, but keep one identifying feature private for ownership checks.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FilterChip(
                    selected = itemType == ItemType.LOST,
                    onClick = { itemType = ItemType.LOST },
                    label = { Text("I lost something") },
                    modifier = Modifier.weight(1f)
                )
                FilterChip(
                    selected = itemType == ItemType.FOUND,
                    onClick = { itemType = ItemType.FOUND },
                    label = { Text("I found something") },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = title,
                onValueChange = { title = it; validationError = null },
                label = { Text("Item name") },
                supportingText = { Text("Example: Black scientific calculator") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))

            ExposedDropdownMenuBox(
                expanded = categoryExpanded,
                onExpandedChange = { categoryExpanded = !categoryExpanded }
            ) {
                OutlinedTextField(
                    value = category,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(categoryExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = categoryExpanded, onDismissRequest = { categoryExpanded = false }) {
                    ItemCategories.all.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = { category = option; categoryExpanded = false; validationError = null }
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = location,
                onValueChange = { location = it; validationError = null },
                label = { Text(if (itemType == ItemType.LOST) "Last seen location" else "Found location") },
                supportingText = { Text("Building, room, landmark or nearby area") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                enabled = !isSubmitting && imageUris.size < 3,
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text(if (imageUris.isEmpty()) " Add up to 3 photos" else " Add another photo (${imageUris.size}/3)")
            }
            if (imageUris.isNotEmpty()) {
                Text(
                    "Tap a photo to remove it",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    items(imageUris) { uri ->
                        AsyncImage(
                            model = uri,
                            contentDescription = "Selected report photo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { imageUris = imageUris - uri }
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = description,
                onValueChange = { description = it; validationError = null },
                label = { Text("Description") },
                supportingText = { Text("Colour, brand and visible features") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = contactInfo,
                onValueChange = { contactInfo = it; validationError = null },
                label = { Text("Safe contact method") },
                supportingText = { Text("Phone, email, or where to hand in the item") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            (validationError ?: submitError)?.let {
                Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 12.dp))
            }
            if (submitError?.startsWith("Image upload failed") == true && imageUris.isNotEmpty()) {
                OutlinedButton(
                    onClick = {
                        imageUris = emptyList()
                        viewModel.clearError()
                    },
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                ) {
                    Text("Remove photos and publish without them")
                }
            }
            Spacer(Modifier.height(22.dp))
            Button(
                enabled = !isSubmitting,
                onClick = {
                    validationError = when {
                        title.trim().length < 3 -> "Use a clear item name."
                        category.isBlank() -> "Choose an item category."
                        location.trim().length < 3 -> "Add a useful location."
                        description.trim().length < 10 -> "Add at least a short description."
                        contactInfo.isBlank() -> "Add a safe contact method."
                        else -> null
                    }
                    if (validationError == null) {
                        viewModel.createItem(
                            itemType,
                            title,
                            description,
                            category,
                            location,
                            contactInfo,
                            userId,
                            imageUris,
                            onNavigateBack
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (isSubmitting) progressMessage ?: "Publishing…" else "Publish report") }
            Spacer(Modifier.height(30.dp))
        }
    }
}
