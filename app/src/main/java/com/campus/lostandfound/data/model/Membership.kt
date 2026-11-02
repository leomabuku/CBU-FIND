package com.campus.lostandfound.data.model

import org.json.JSONObject

enum class MembershipStatus {
    UNVERIFIED,
    PENDING,
    VERIFIED,
    REJECTED;

    companion object {
        fun fromWire(value: String?): MembershipStatus =
            values().firstOrNull { it.name == value?.uppercase() } ?: UNVERIFIED
    }
}

data class VerificationSummary(
    val id: String = "",
    val status: String = "DRAFT",
    val studentNumber: String = "",
    val fullName: String = "",
    val programme: String = "",
    val academicYear: String = "",
    val studentNumberConfidence: Double = 0.0,
    val ocrProvider: String = "",
    val matchOutcome: String = "",
    val submittedAt: Long = 0L,
    val decisionReason: String = ""
) {
    companion object {
        fun fromJson(json: JSONObject?): VerificationSummary? {
            if (json == null || json.isNull("id")) return null
            val confidence = json.optJSONObject("confidence")
            return VerificationSummary(
                id = json.optString("id"),
                status = json.optString("status", "DRAFT"),
                studentNumber = json.optString("studentNumber"),
                fullName = json.optString("fullName"),
                programme = json.optString("programme"),
                academicYear = json.optString("academicYear"),
                studentNumberConfidence = confidence?.optDouble("studentNumber", 0.0) ?: 0.0,
                ocrProvider = json.optString("ocrProvider"),
                matchOutcome = json.optString("matchOutcome"),
                submittedAt = json.optLong("submittedAt"),
                decisionReason = json.optString("decisionReason")
            )
        }
    }
}

/**
 * Mirrors MembershipRecord in packages/contracts. Membership is separate from the
 * account role: a moderator is not automatically a verified student.
 */
data class Membership(
    val status: MembershipStatus = MembershipStatus.UNVERIFIED,
    val institutionId: String = "cbu",
    val updatedAt: Long = 0L,
    val request: VerificationSummary? = null
) {
    /** Reporting, claiming and messaging require verification; staff are exempt server-side. */
    val canAct: Boolean get() = status == MembershipStatus.VERIFIED

    companion object {
        fun fromJson(json: JSONObject): Membership = Membership(
            status = MembershipStatus.fromWire(json.optString("status")),
            institutionId = json.optString("institutionId", "cbu"),
            updatedAt = json.optLong("updatedAt"),
            request = VerificationSummary.fromJson(json.optJSONObject("request"))
        )
    }
}

data class VerificationDraft(
    val requestId: String,
    val studentNumber: String,
    val fullName: String,
    val programme: String,
    val academicYear: String,
    val studentNumberConfidence: Double,
    val extractionAvailable: Boolean
)
