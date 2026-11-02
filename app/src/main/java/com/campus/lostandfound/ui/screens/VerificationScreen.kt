package com.campus.lostandfound.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campus.lostandfound.data.model.MembershipStatus
import com.campus.lostandfound.ui.viewmodel.VerificationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VerificationScreen(viewModel: VerificationViewModel, onBack: () -> Unit) {
    val membership by viewModel.membership.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val draft by viewModel.draft.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) viewModel.scan(uri)
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Student verification") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }
        )
    }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(viewModel.statusLabel(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        when (membership.status) {
                            MembershipStatus.VERIFIED -> "You can report items, claim them and message other students."
                            MembershipStatus.PENDING -> "A moderator is reviewing your document. You can browse while you wait."
                            MembershipStatus.REJECTED -> "Check the moderator note below, then submit a clearer document."
                            MembershipStatus.UNVERIFIED -> "Upload a photo of your CBU student ID to report, claim and message."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    membership.request?.let { request ->
                        Text("State: ${request.status.replace('_', ' ').lowercase()}", style = MaterialTheme.typography.labelMedium)
                        if (request.decisionReason.isNotBlank()) {
                            Text("Moderator note: ${request.decisionReason}", color = MaterialTheme.colorScheme.error)
                        }
                    }
                    if (isLoading) CircularProgressIndicator(Modifier.padding(top = 8.dp))
                }
            }

            Text(
                "Your ID photo is stored privately and is only opened by a moderator during review. It is never shown publicly.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            val submitter = draft
            if (submitter == null) {
                val canSubmit = membership.status == MembershipStatus.UNVERIFIED || membership.status == MembershipStatus.REJECTED
                if (canSubmit) {
                    Button(enabled = !isBusy, onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (isBusy) "Uploading and reading…" else "Choose student ID photo")
                    }
                    Text(
                        "JPEG, PNG or WebP · up to 5 MB · the printed student number must be readable.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                VerificationForm(
                    initialNumber = submitter.studentNumber,
                    initialName = submitter.fullName,
                    initialProgramme = submitter.programme,
                    initialYear = submitter.academicYear,
                    confidence = submitter.studentNumberConfidence,
                    extractionAvailable = submitter.extractionAvailable,
                    busy = isBusy,
                    onCancel = viewModel::cancelDraft,
                    onSubmit = viewModel::confirm
                )
            }

            message?.let { Text(it, color = MaterialTheme.colorScheme.tertiary) }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun VerificationForm(
    initialNumber: String,
    initialName: String,
    initialProgramme: String,
    initialYear: String,
    confidence: Double,
    extractionAvailable: Boolean,
    busy: Boolean,
    onCancel: () -> Unit,
    onSubmit: (String, String, String, String) -> Unit
) {
    var studentNumber by remember { mutableStateOf(initialNumber) }
    var fullName by remember { mutableStateOf(initialName) }
    var programme by remember { mutableStateOf(initialProgramme) }
    var academicYear by remember { mutableStateOf(initialYear) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Confirm the details on your card", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (extractionAvailable && confidence > 0) {
                Text(
                    "Student number read with ${(confidence * 100).toInt()}% confidence. Always check it.",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(studentNumber, { studentNumber = it }, label = { Text("Student number") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(fullName, { fullName = it }, label = { Text("Full name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(programme, { programme = it }, label = { Text("Programme (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(academicYear, { academicYear = it }, label = { Text("Academic year (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel, enabled = !busy) { Text("Cancel") }
                Button(
                    enabled = !busy && studentNumber.isNotBlank() && fullName.isNotBlank(),
                    onClick = { onSubmit(studentNumber, fullName, programme, academicYear) }
                ) { Text(if (busy) "Submitting…" else "Submit for verification") }
            }
        }
    }
}
