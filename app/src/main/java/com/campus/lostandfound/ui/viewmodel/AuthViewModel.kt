package com.campus.lostandfound.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.lostandfound.data.model.User
import com.campus.lostandfound.data.repository.AppRepository
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AuthViewModel(
    private val auth: FirebaseAuth,
    private val repository: AppRepository
) : ViewModel() {
    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser: StateFlow<User?> = _currentUser.asStateFlow()
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()
    private val _isAuthReady = MutableStateFlow(false)
    val isAuthReady: StateFlow<Boolean> = _isAuthReady.asStateFlow()
    private var profileJob: Job? = null
    private var authGeneration = 0L
    private var pendingRegistration: User? = null

    private val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        val firebaseUser = firebaseAuth.currentUser
        val generation = ++authGeneration
        profileJob?.cancel()
        if (firebaseUser == null) {
            _currentUser.value = null
            _isAuthReady.value = true
            _isBusy.value = false
            return@AuthStateListener
        }
        _isAuthReady.value = false
        profileJob = viewModelScope.launch {
            try {
                var profile = repository.getUserById(firebaseUser.uid)
                if (profile == null) {
                    val now = System.currentTimeMillis()
                    val fallback = pendingRegistration?.copy(id = firebaseUser.uid, createdAt = now, updatedAt = now) ?: User(
                        id = firebaseUser.uid,
                        name = firebaseUser.displayName ?: firebaseUser.email?.substringBefore('@') ?: "CBU Find member",
                        email = firebaseUser.email.orEmpty(),
                        photoUrl = firebaseUser.photoUrl?.toString().orEmpty(),
                        createdAt = now,
                        updatedAt = now
                    )
                    repository.registerUser(fallback)
                    var attempts = 0
                    while (profile == null && attempts < 4) {
                        delay(250)
                        profile = repository.getUserById(firebaseUser.uid)
                        attempts += 1
                    }
                    profile = profile ?: fallback
                }
                if (generation == authGeneration && auth.currentUser?.uid == firebaseUser.uid) {
                    _currentUser.value = profile
                    _authError.value = null
                }
            } catch (error: Exception) {
                if (generation == authGeneration) {
                    _currentUser.value = null
                    _authError.value = "Your account signed in, but the private profile could not load. Retry or sign out. ${safeMessage(error)}"
                }
            } finally {
                if (generation == authGeneration) {
                    _isAuthReady.value = true
                    _isBusy.value = false
                }
            }
        }
    }

    init { auth.addAuthStateListener(authListener) }

    fun register(name: String, studentId: String, email: String, password: String) = launchAuth {
        val now = System.currentTimeMillis()
        pendingRegistration = User(id = "", name = name.trim(), studentId = studentId.trim(), email = email.trim(), createdAt = now, updatedAt = now)
        var createdUser: com.google.firebase.auth.FirebaseUser? = null
        try {
            createdUser = auth.createUserWithEmailAndPassword(email.trim(), password).await().user
            repository.registerUser(pendingRegistration!!.copy(id = requireNotNull(createdUser).uid))
            pendingRegistration = null
            refreshProfile()
        } catch (error: Exception) {
            pendingRegistration = null
            runCatching { createdUser?.delete()?.await() }
            throw IllegalStateException("Account setup did not finish, so the new sign-in was rolled back safely. ${safeMessage(error)}", error)
        }
    }

    fun login(email: String, password: String) = launchAuth { auth.signInWithEmailAndPassword(email.trim(), password).await() }
    fun signInWithCredential(credential: AuthCredential) = launchAuth { auth.signInWithCredential(credential).await() }

    fun sendPasswordReset(email: String, onSent: () -> Unit) = launchAuth {
        auth.sendPasswordResetEmail(email.trim()).await()
        onSent()
    }

    fun reportGoogleSignInError() { _authError.value = "Google sign-in was cancelled or could not complete. Retry, or use email and password. Reference: ANDROID-GOOGLE-AUTH" }
    fun clearError() { _authError.value = null }
    fun logout() { auth.signOut() }

    fun refreshProfile() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching { repository.getUserById(uid) }
                .onSuccess { user -> if (user != null && auth.currentUser?.uid == uid) _currentUser.value = user }
                .onFailure { _authError.value = safeMessage(it) }
        }
    }

    private fun launchAuth(block: suspend () -> Unit) {
        if (_isBusy.value) return
        viewModelScope.launch {
            _isBusy.value = true; _authError.value = null
            try { block() } catch (error: Exception) { _authError.value = safeMessage(error); _isBusy.value = false; _isAuthReady.value = true }
        }
    }

    private fun safeMessage(error: Throwable): String {
        val code = (error as? FirebaseAuthException)?.errorCode.orEmpty()
        return when (code) {
            "ERROR_INVALID_EMAIL" -> "Enter a valid email address. Reference: AUTH-INVALID-EMAIL"
            "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_USER_NOT_FOUND" -> "The email or password is incorrect. Reference: AUTH-CREDENTIAL"
            "ERROR_EMAIL_ALREADY_IN_USE" -> "An account already uses that email. Sign in or reset the password. Reference: AUTH-EMAIL-IN-USE"
            "ERROR_WEAK_PASSWORD" -> "Use a stronger password with at least six characters. Reference: AUTH-WEAK-PASSWORD"
            "ERROR_TOO_MANY_REQUESTS" -> "Too many attempts. Wait a few minutes, then retry. Reference: AUTH-RATE-LIMIT"
            "ERROR_NETWORK_REQUEST_FAILED" -> "Authentication is offline. Check your connection and retry. Reference: AUTH-NETWORK"
            else -> error.message?.takeIf { "Reference:" in it } ?: "Authentication could not complete. Retry the action. Reference: AUTH-UNEXPECTED"
        }
    }

    override fun onCleared() { auth.removeAuthStateListener(authListener); profileJob?.cancel(); super.onCleared() }
}
