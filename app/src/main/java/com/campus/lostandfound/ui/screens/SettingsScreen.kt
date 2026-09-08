package com.campus.lostandfound.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campus.lostandfound.ui.viewmodel.SettingsViewModel
import com.campus.lostandfound.ui.theme.ThemeMode
import com.google.firebase.messaging.FirebaseMessaging

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    themeMode: ThemeMode,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onBack: () -> Unit,
    onDeletionQueued: () -> Unit
) {
    val preferences by viewModel.preferences.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val blockedAccounts by viewModel.blockedAccounts.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    fun requestToken() {
        FirebaseMessaging.getInstance().token
            .addOnSuccessListener(viewModel::registerToken)
            .addOnFailureListener(viewModel::reportPushTokenFailure)
    }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) requestToken() else viewModel.reportPushPermissionDenied()
    }
    fun enablePush() { if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS) else requestToken() }
    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp)) {
            Text("Appearance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text("Choose how CBU Find looks on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            Row(Modifier.fillMaxWidth()) {
                AppearanceChoice("Light", false, themeMode == ThemeMode.LIGHT, { onThemeModeChanged(ThemeMode.LIGHT) }, Modifier.weight(1f))
                AppearanceChoice("Dark", true, themeMode == ThemeMode.DARK, { onThemeModeChanged(ThemeMode.DARK) }, Modifier.weight(1f).padding(start = 10.dp))
            }
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Follow device", fontWeight = FontWeight.SemiBold)
                    Text("Switch automatically with your phone.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(themeMode == ThemeMode.SYSTEM, { enabled -> onThemeModeChanged(if (enabled) ThemeMode.SYSTEM else ThemeMode.LIGHT) })
            }
            HorizontalDivider(Modifier.padding(vertical = 22.dp))
            Text("Notifications", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            preferences.forEach { (key, value) -> Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(preferenceLabel(key), fontWeight = FontWeight.SemiBold); if (key == "showMessagePreview") Text("Off by default for privacy.", color = MaterialTheme.colorScheme.onSurfaceVariant) }; Switch(value, { viewModel.toggle(key, it) }) } }
            Button(onClick = ::enablePush, enabled = !working, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("Enable push on this device") }
            TextButton(onClick = viewModel::save, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Save preferences") }
            message?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(vertical = 8.dp)) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(vertical = 8.dp)) }
            HorizontalDivider(Modifier.padding(vertical = 22.dp))
            Text("Blocked accounts", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (blockedAccounts.isEmpty()) Text("You have not blocked any accounts.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
            blockedAccounts.forEach { uid -> Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Text(uid, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); TextButton(onClick = { viewModel.unblock(uid) }, enabled = !working) { Text("Unblock") } } }
            HorizontalDivider(Modifier.padding(vertical = 22.dp))
            Text("Delete account", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text("Credentials and private data are deleted. Shared history is anonymized, and active reports and known media are removed.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 10.dp))
            Button(onClick = { confirmDelete = true }, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("Delete my account") }
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { if (!working) confirmDelete = false }, title = { Text("Delete account?") }, text = { Text("This cannot be undone. Cleanup may continue briefly after you are signed out.") }, confirmButton = { Button(enabled = !working, onClick = { viewModel.deleteAccount { confirmDelete = false; onDeletionQueued() } }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

@Composable
private fun AppearanceChoice(label: String, dark: Boolean, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val previewBackground = if (dark) androidx.compose.ui.graphics.Color(0xFF171C1A) else androidx.compose.ui.graphics.Color(0xFFFFFBF5)
    val previewSurface = if (dark) androidx.compose.ui.graphics.Color(0xFF29302D) else androidx.compose.ui.graphics.Color.White
    Card(
        modifier = modifier.height(112.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(Modifier.fillMaxSize().padding(10.dp)) {
            Column(Modifier.fillMaxWidth().height(58.dp).background(previewBackground, RoundedCornerShape(10.dp)).padding(8.dp)) {
                androidx.compose.foundation.layout.Box(Modifier.fillMaxWidth(.62f).height(8.dp).background(previewSurface, RoundedCornerShape(8.dp)))
                androidx.compose.foundation.layout.Box(Modifier.padding(top = 7.dp).fillMaxWidth().height(20.dp).background(previewSurface, RoundedCornerShape(8.dp)))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = onClick)
                Text(label, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun preferenceLabel(key: String) = when (key) { "claims" -> "New claims"; "claimDecisions" -> "Claim decisions"; "messages" -> "Messages"; "reportUpdates" -> "Report updates"; "moderation" -> "Moderation actions"; else -> "Show message previews" }
