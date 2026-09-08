package com.campus.lostandfound.ui.screens

import android.util.Patterns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campus.lostandfound.R
import com.campus.lostandfound.ui.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.GoogleAuthProvider

private enum class AuthMode { SIGN_IN, REGISTER, RESET }

@Composable
fun AuthScreen(viewModel: AuthViewModel, onLoginSuccess: () -> Unit) {
    val context = LocalContext.current
    var mode by remember { mutableStateOf(AuthMode.SIGN_IN) }
    var name by remember { mutableStateOf("") }
    var studentId by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val googleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        runCatching { GoogleSignIn.getSignedInAccountFromIntent(result.data).result }
            .onSuccess { account -> account.idToken?.let { viewModel.signInWithCredential(GoogleAuthProvider.getCredential(it, null)) } ?: viewModel.reportGoogleSignInError() }
            .onFailure { viewModel.reportGoogleSignInError() }
    }
    LaunchedEffect(currentUser?.id) { if (currentUser != null) onLoginSuccess() }

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.onBackground).verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Image(painterResource(R.drawable.cbu_find_logo), "CBU Find", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(120.dp))
        Text(
            when (mode) { AuthMode.REGISTER -> "Join the campus network"; AuthMode.RESET -> "Recover your account"; AuthMode.SIGN_IN -> "Find what matters." },
            style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.background, modifier = Modifier.padding(top = 14.dp, bottom = 7.dp)
        )
        Text("Report, verify and return items across Android and web.", color = MaterialTheme.colorScheme.background.copy(alpha = .78f))
        Spacer(Modifier.height(24.dp))
        Card(shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(10.dp)) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(when (mode) { AuthMode.REGISTER -> "Create your account"; AuthMode.RESET -> "Reset password"; AuthMode.SIGN_IN -> "Welcome back" }, style = MaterialTheme.typography.headlineMedium)
                Text(
                    if (mode == AuthMode.RESET) "We’ll email a secure reset link." else "Use email/password or Google. Phone-number sign-in has been retired.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (mode == AuthMode.REGISTER) {
                    OutlinedTextField(name, { name = it; localError = null }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(studentId, { studentId = it; localError = null }, label = { Text("Student ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                OutlinedTextField(email, { email = it; localError = null }, label = { Text("Email address") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), singleLine = true, modifier = Modifier.fillMaxWidth())
                if (mode != AuthMode.RESET) OutlinedTextField(password, { password = it; localError = null }, label = { Text("Password") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                (localError ?: authError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                confirmation?.let { Text(it, color = Color(0xFF13743E)) }
                Button(
                    enabled = !isBusy,
                    onClick = {
                        localError = validate(mode, name, studentId, email, password)
                        if (localError == null) when (mode) {
                            AuthMode.REGISTER -> viewModel.register(name, studentId, email, password)
                            AuthMode.SIGN_IN -> viewModel.login(email, password)
                            AuthMode.RESET -> viewModel.sendPasswordReset(email) { confirmation = "Reset email sent. Check your inbox and spam folder." }
                        }
                    }, modifier = Modifier.fillMaxWidth()
                ) { Text(if (isBusy) "Please wait…" else when (mode) { AuthMode.REGISTER -> "Create account"; AuthMode.RESET -> "Send reset link"; AuthMode.SIGN_IN -> "Sign in" }) }

                if (mode != AuthMode.RESET) {
                    Row { HorizontalDivider(Modifier.weight(1f).padding(top = 10.dp)); Text("  or  ", color = MaterialTheme.colorScheme.onSurfaceVariant); HorizontalDivider(Modifier.weight(1f).padding(top = 10.dp)) }
                    OutlinedButton(
                        enabled = !isBusy,
                        onClick = {
                            val options = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestIdToken(context.getString(R.string.default_web_client_id)).requestEmail().build()
                            googleLauncher.launch(GoogleSignIn.getClient(context, options).signInIntent)
                        }, modifier = Modifier.fillMaxWidth()
                    ) { Text("Continue with Google") }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { mode = if (mode == AuthMode.SIGN_IN) AuthMode.REGISTER else AuthMode.SIGN_IN; localError = null; confirmation = null; viewModel.clearError() }) { Text(if (mode == AuthMode.SIGN_IN) "Create account" else "Back to sign in") }
                    if (mode == AuthMode.SIGN_IN) TextButton(onClick = { mode = AuthMode.RESET; localError = null; viewModel.clearError() }) { Text("Forgot password?") }
                }
            }
        }
    }
}

private fun validate(mode: AuthMode, name: String, studentId: String, email: String, password: String): String? = when {
    mode == AuthMode.REGISTER && name.trim().length < 2 -> "Enter your full name."
    mode == AuthMode.REGISTER && studentId.isBlank() -> "Enter your student ID."
    !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> "Enter a valid email address."
    mode != AuthMode.RESET && password.length < 6 -> "Password must contain at least 6 characters."
    else -> null
}
