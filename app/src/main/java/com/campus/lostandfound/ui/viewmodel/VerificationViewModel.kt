package com.campus.lostandfound.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.campus.lostandfound.data.model.Membership
import com.campus.lostandfound.data.model.MembershipStatus
import com.campus.lostandfound.data.model.VerificationDraft
import com.campus.lostandfound.data.model.VerificationSummary
import com.campus.lostandfound.data.repository.AppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class VerificationViewModel(private val repository: AppRepository) : ViewModel() {
    private val _membership = MutableStateFlow(Membership())
    val membership: StateFlow<Membership> = _membership.asStateFlow()
    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()
    private val _draft = MutableStateFlow<VerificationDraft?>(null)
    val draft: StateFlow<VerificationDraft?> = _draft.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    private val _queue = MutableStateFlow<List<VerificationSummary>>(emptyList())
    val queue: StateFlow<List<VerificationSummary>> = _queue.asStateFlow()

    fun load() = viewModelScope.launch {
        _isLoading.value = true
        try { _membership.value = repository.getMembership() }
        catch (error: Exception) { _error.value = error.message ?: "Could not load your membership." }
        finally { _isLoading.value = false }
    }

    fun scan(uri: Uri) = viewModelScope.launch {
        _isBusy.value = true; _error.value = null; _message.value = null
        try {
            val result = repository.scanStudentId(uri)
            _draft.value = result
            _message.value = if (result.extractionAvailable) {
                "Check the details read from your card, correct anything wrong, then submit."
            } else {
                "Automatic reading is unavailable. Type the details exactly as printed; a moderator will compare them with your document."
            }
        } catch (error: Exception) {
            _error.value = error.message ?: "That image could not be processed. Try a clearer photograph."
        } finally { _isBusy.value = false }
    }

    fun confirm(studentNumber: String, fullName: String, programme: String, academicYear: String) = viewModelScope.launch {
        val current = _draft.value ?: return@launch
        if (studentNumber.isBlank() || fullName.isBlank()) {
            _error.value = "Enter your student number and full name."
            return@launch
        }
        _isBusy.value = true; _error.value = null
        try {
            val status = repository.confirmVerification(
                current.copy(
                    studentNumber = studentNumber,
                    fullName = fullName,
                    programme = programme,
                    academicYear = academicYear
                )
            )
            _draft.value = null
            _message.value = if (status == "APPROVED") "Your student membership is verified." else "Submitted for moderator review. You will be notified."
            _membership.value = repository.getMembership()
        } catch (error: Exception) {
            _error.value = error.message ?: "Your verification could not be submitted."
        } finally { _isBusy.value = false }
    }

    fun cancelDraft() { _draft.value = null; _message.value = null }

    fun clearFeedback() { _error.value = null; _message.value = null }

    fun loadQueue() = viewModelScope.launch {
        try { _queue.value = repository.getVerificationQueue() }
        catch (error: Exception) { _error.value = error.message ?: "Could not load the verification queue." }
    }

    fun decide(requestId: String, decision: String, reason: String) = viewModelScope.launch {
        if (decision != "APPROVE" && reason.isBlank()) {
            _error.value = "Give the student a reason before rejecting or requesting a new document."
            return@launch
        }
        _isBusy.value = true; _error.value = null
        try {
            repository.decideVerification(requestId, decision, reason)
            _message.value = when (decision) {
                "APPROVE" -> "Membership approved."
                "REJECT" -> "Verification rejected and the student notified."
                else -> "Resubmission requested."
            }
            _queue.value = repository.getVerificationQueue()
        } catch (error: Exception) {
            _error.value = error.message ?: "That decision could not be saved."
        } finally { _isBusy.value = false }
    }

    fun statusLabel(): String = when (_membership.value.status) {
        MembershipStatus.VERIFIED -> "Verified CBU student"
        MembershipStatus.PENDING -> "Verification awaiting review"
        MembershipStatus.REJECTED -> "Verification not approved"
        MembershipStatus.UNVERIFIED -> "Membership not verified"
    }
}
