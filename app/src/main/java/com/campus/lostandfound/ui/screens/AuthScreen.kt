package com.campus.lostandfound.ui.screens

import android.app.Activity
import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campus.lostandfound.R
import com.campus.lostandfound.ui.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider

@Composable
fun AuthScreen(viewModel: AuthViewModel, onLoginSuccess: () -> Unit) {
    var isRegistering by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var showPhoneDialog by remember { mutableStateOf(false) }

    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    .getResult(ApiException::class.java)
                account.idToken?.let { token ->
                    viewModel.signInWithCredential(GoogleAuthProvider.getCredential(token, null))
                } ?: viewModel.reportGoogleSignInError()
            } catch (_: ApiException) {
                viewModel.reportGoogleSignInError()
            }
        }
    }

    LaunchedEffect(currentUser) { if (currentUser != null) onLoginSuccess() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("CBU FIND", color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.ExtraBold)
            Text(
                text = if (isRegistering) "Join the campus network" else "Find what matters.",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
            )
            Text(
                "A trusted space for Copperbelt University students to report, match and return items.",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = .82f)
            )
            Spacer(Modifier.height(28.dp))

            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(10.dp)
            ) {
                Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        if (isRegistering) "Create your account" else "Welcome back",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        if (isRegistering) "Use details your classmates can recognise."
                        else "Sign in to view reports and help return items.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (isRegistering) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it; localError = null },
                            label = { Text("Full name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = studentId,
                            onValueChange = { studentId = it; localError = null },
                            label = { Text("Student ID") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; localError = null },
                        label = { Text("Email address") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; localError = null },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    (localError ?: authError)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                    }

                    Button(
                        enabled = !isBusy,
                        onClick = {
                            localError = validateAuthForm(isRegistering, name, studentId, email, password)
                            if (localError == null) {
                                if (isRegistering) viewModel.register(name, studentId, email, password)
                                else viewModel.login(email, password)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isBusy) "Please wait…" else if (isRegistering) "Create account" else "Sign in")
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        HorizontalDivider(Modifier.weight(1f))
                        Text("  or  ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        HorizontalDivider(Modifier.weight(1f))
                    }

                    OutlinedButton(
                        enabled = !isBusy,
                        onClick = {
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestIdToken(context.getString(R.string.default_web_client_id))
                                .requestEmail()
                                .build()
                            googleSignInLauncher.launch(GoogleSignIn.getClient(context, gso).signInIntent)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Continue with Google") }

                    OutlinedButton(
                        enabled = !isBusy,
                        onClick = { showPhoneDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Continue with phone") }

                    TextButton(
                        onClick = {
                            isRegistering = !isRegistering
                            localError = null
                            viewModel.clearError()
                        },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(if (isRegistering) "Already registered? Sign in" else "New here? Create an account")
                    }
                }
            }
        }
    }

    if (showPhoneDialog) {
        PhoneSignInDialog(
            viewModel = viewModel,
            activity = context as Activity,
            onDismiss = { showPhoneDialog = false }
        )
    }
}

@Composable
private fun PhoneSignInDialog(
    viewModel: AuthViewModel,
    activity: Activity,
    onDismiss: () -> Unit
) {
    var phone by remember { mutableStateOf("+260") }
    var code by remember { mutableStateOf("") }
    val codeSent by viewModel.phoneCodeSent.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()

    AlertDialog(
        onDismissRequest = { if (!isBusy) onDismiss() },
        title = { Text(if (codeSent) "Enter verification code" else "Sign in with phone") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (codeSent) "Enter the 6-digit SMS code sent to $phone."
                    else "Use international format, for example +260970000000.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!codeSent) {
                    OutlinedTextField(
                        value = phone,
                        onValueChange = { phone = it },
                        label = { Text("Phone number") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true
                    )
                } else {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it.filter(Char::isDigit).take(6) },
                        label = { Text("SMS code") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
                authError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = !isBusy && if (codeSent) code.length == 6 else phone.startsWith("+") && phone.length >= 10,
                onClick = {
                    if (codeSent) viewModel.verifyPhoneCode(code)
                    else viewModel.sendPhoneCode(activity, phone)
                }
            ) {
                Text(if (isBusy) "Please wait…" else if (codeSent) "Verify and sign in" else "Send code")
            }
        },
        dismissButton = { TextButton(enabled = !isBusy, onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun validateAuthForm(
    registering: Boolean,
    name: String,
    studentId: String,
    email: String,
    password: String
): String? = when {
    registering && name.trim().length < 2 -> "Enter your full name."
    registering && studentId.isBlank() -> "Enter your student ID."
    !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address."
    password.length < 6 -> "Password must contain at least 6 characters."
    else -> null
}
