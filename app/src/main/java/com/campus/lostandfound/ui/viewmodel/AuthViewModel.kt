package com.campus.lostandfound.ui.viewmodel

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.lostandfound.data.model.User
import com.campus.lostandfound.data.repository.AppRepository
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.TimeUnit

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

    private val _phoneCodeSent = MutableStateFlow(false)
    val phoneCodeSent: StateFlow<Boolean> = _phoneCodeSent.asStateFlow()
    private var phoneVerificationId: String? = null

    init {
        // Observe Firebase Auth state
        auth.addAuthStateListener { firebaseAuth ->
            val firebaseUser = firebaseAuth.currentUser
            if (firebaseUser != null) {
                // Fetch our custom User object from Firestore
                viewModelScope.launch {
                    try {
                        val user = repository.getUserById(firebaseUser.uid)
                        if (user != null) {
                            val hydrated = user.copy(
                                name = user.name.ifBlank { firebaseUser.displayName.orEmpty() },
                                photoUrl = user.photoUrl.ifBlank { firebaseUser.photoUrl?.toString().orEmpty() },
                                phone = user.phone.ifBlank { firebaseUser.phoneNumber.orEmpty() }
                            )
                            _currentUser.value = hydrated
                            if (hydrated != user) runCatching { repository.updateUser(hydrated) }
                        } else {
                            // If user document doesn't exist yet (e.g., Google Sign-in first time)
                            val newUser = User(
                                id = firebaseUser.uid,
                                name = firebaseUser.displayName ?: "",
                                email = firebaseUser.email ?: "",
                                studentId = "",
                                phone = firebaseUser.phoneNumber.orEmpty(),
                                photoUrl = firebaseUser.photoUrl?.toString().orEmpty(),
                                createdAt = System.currentTimeMillis()
                            )
                            repository.registerUser(newUser)
                            _currentUser.value = newUser
                        }
                    } catch (e: Exception) {
                        _currentUser.value = User(
                            id = firebaseUser.uid,
                            name = firebaseUser.displayName.orEmpty(),
                            email = firebaseUser.email.orEmpty(),
                            phone = firebaseUser.phoneNumber.orEmpty(),
                            photoUrl = firebaseUser.photoUrl?.toString().orEmpty()
                        )
                        _authError.value = "Signed in, but profile sync is offline. Check Firestore setup."
                    } finally {
                        _isAuthReady.value = true
                    }
                }
            } else {
                _currentUser.value = null
                _isAuthReady.value = true
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                auth.signInWithEmailAndPassword(email.trim(), password).await()
                _authError.value = null
            } catch (e: Exception) {
                _authError.value = readableAuthError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun register(name: String, studentId: String, email: String, password: String) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                val result = auth.createUserWithEmailAndPassword(email.trim(), password).await()
                val firebaseUser = result.user
                if (firebaseUser != null) {
                    val newUser = User(
                        id = firebaseUser.uid,
                        name = name.trim(),
                        studentId = studentId.trim(),
                        email = email.trim(),
                        createdAt = System.currentTimeMillis()
                    )
                    repository.registerUser(newUser)
                    _currentUser.value = newUser
                    _authError.value = null
                }
            } catch (e: Exception) {
                _authError.value = readableAuthError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }
    
    fun signInWithCredential(credential: AuthCredential) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                auth.signInWithCredential(credential).await()
                _authError.value = null
            } catch (e: Exception) {
                _authError.value = readableAuthError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun reportGoogleSignInError() {
        _authError.value = "Google sign-in was cancelled or could not be completed."
    }

    fun sendPhoneCode(activity: Activity, phoneNumber: String) {
        _isBusy.value = true
        _authError.value = null
        _phoneCodeSent.value = false
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber.trim())
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithPhoneCredential(credential)
                }

                override fun onVerificationFailed(exception: com.google.firebase.FirebaseException) {
                    _isBusy.value = false
                    _authError.value = readableAuthError(exception)
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    phoneVerificationId = verificationId
                    _phoneCodeSent.value = true
                    _isBusy.value = false
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyPhoneCode(code: String) {
        val verificationId = phoneVerificationId
        if (verificationId == null) {
            _authError.value = "Request a verification code first."
            return
        }
        signInWithPhoneCredential(PhoneAuthProvider.getCredential(verificationId, code.trim()))
    }

    private fun signInWithPhoneCredential(credential: PhoneAuthCredential) {
        viewModelScope.launch {
            _isBusy.value = true
            try {
                auth.signInWithCredential(credential).await()
                _phoneCodeSent.value = false
                _authError.value = null
            } catch (e: Exception) {
                _authError.value = readableAuthError(e)
            } finally {
                _isBusy.value = false
            }
        }
    }

    fun refreshProfile() {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _currentUser.value = repository.getUserById(uid)
        }
    }

    fun logout() {
        auth.signOut()
        _currentUser.value = null
    }

    fun clearError() {
        _authError.value = null
    }

    private fun readableAuthError(error: Exception): String {
        val message = error.message.orEmpty().lowercase()
        return when {
            "password" in message && "invalid" in message -> "The email or password is incorrect."
            "email address is badly formatted" in message -> "Enter a valid email address."
            "already in use" in message -> "An account already exists for this email."
            "credential" in message && ("incorrect" in message || "malformed" in message) ->
                "Those sign-in details are incorrect or expired. Try again."
            error is FirebaseAuthException && error.errorCode == "ERROR_INVALID_VERIFICATION_CODE" ->
                "That verification code is incorrect."
            "quota" in message -> "The SMS quota has been reached. Use a Firebase test number or try again tomorrow."
            "sms unable to be sent until this region" in message ->
                "SMS sign-in is not enabled for Zambia. In Firebase Authentication → Settings → SMS region policy, allow Zambia."
            "operation is not allowed" in message ->
                "This sign-in method is not fully enabled in Firebase Authentication settings."
            "network" in message -> "Check your internet connection and try again."
            else -> error.message ?: "Authentication failed. Please try again."
        }
    }
}
