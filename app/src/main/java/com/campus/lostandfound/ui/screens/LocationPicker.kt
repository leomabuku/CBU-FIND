package com.campus.lostandfound.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun LocationPicker(
    value: String,
    label: String,
    onValueChange: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var locationError by remember { mutableStateOf<String?>(null) }
    var searchJob by remember { mutableStateOf<Job?>(null) }

    fun useCurrentArea() {
        scope.launch {
            searching = true
            locationError = null
            runCatching { nearbyReferencePoints(context) }
                .onSuccess {
                    suggestions = it
                    if (it.isEmpty()) locationError = "No nearby reference points found. Enter a campus location instead."
                }
                .onFailure { locationError = "Current location is unavailable. Search or enter a place name." }
            searching = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) useCurrentArea()
        else locationError = "Location permission was not granted. You can still search or type a place."
    }

    LaunchedEffect(value) {
        searchJob?.cancel()
        if (value.trim().length < 3) {
            suggestions = emptyList()
            return@LaunchedEffect
        }
        searchJob = scope.launch {
            delay(450)
            searching = true
            suggestions = searchPlaces(context, value)
            searching = false
        }
    }

    Column {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                locationError = null
            },
            label = { Text(label) },
            placeholder = { Text("Search a real place or type your own") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (searching) CircularProgressIndicator(Modifier.padding(12.dp), strokeWidth = 2.dp)
                else IconButton(onClick = {
                    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                    if (granted) useCurrentArea() else permissionLauncher.launch(
                        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                    )
                }) {
                    Icon(Icons.Default.LocationOn, contentDescription = "Find nearby reference points")
                }
            },
            supportingText = { Text(locationError ?: "Tap the location icon for nearby references, or keep your custom name.") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        if (suggestions.isNotEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                shape = MaterialTheme.shapes.large,
                tonalElevation = 3.dp
            ) {
                Column {
                    suggestions.take(5).forEach { suggestion ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable {
                                onValueChange(suggestion)
                                suggestions = emptyList()
                            }.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Text(suggestion, modifier = Modifier.padding(start = 10.dp))
                        }
                    }
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
private suspend fun searchPlaces(context: Context, query: String): List<String> = withContext(Dispatchers.IO) {
    if (!Geocoder.isPresent()) return@withContext emptyList()
    runCatching {
        Geocoder(context, Locale.getDefault()).getFromLocationName(query, 5).orEmpty()
            .map(::formatAddress)
            .filter { it.isNotBlank() }
            .distinct()
    }.getOrDefault(emptyList())
}

@Suppress("DEPRECATION", "MissingPermission")
private suspend fun nearbyReferencePoints(context: Context): List<String> {
    val location = LocationServices.getFusedLocationProviderClient(context).lastLocation.await()
        ?: error("No cached location")
    return withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext emptyList()
        Geocoder(context, Locale.getDefault()).getFromLocation(location.latitude, location.longitude, 5).orEmpty()
            .map(::formatAddress)
            .filter { it.isNotBlank() }
            .distinct()
    }
}

private fun formatAddress(address: Address): String = buildList {
    address.featureName?.takeIf { it != address.subLocality }?.let(::add)
    address.subLocality?.let(::add)
    address.locality?.let(::add)
    address.adminArea?.let(::add)
}.distinct().joinToString(", ").ifBlank { address.getAddressLine(0).orEmpty() }
