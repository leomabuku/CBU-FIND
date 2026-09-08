package com.campus.lostandfound.data.model

import com.google.firebase.firestore.DocumentId

enum class ClaimStatus { PENDING, ACCEPTED, REJECTED, CANCELLED }
enum class ClaimKind { FOUND_IT, THIS_IS_MINE }

data class Claim(
    @DocumentId val id: String = "",
    val itemId: String = "",
    val itemOwnerId: String = "",
    val claimantId: String = "",
    val kind: ClaimKind = ClaimKind.FOUND_IT,
    val note: String = "",
    val status: ClaimStatus = ClaimStatus.PENDING,
    val conversationId: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    companion object {
        fun fromFirestore(id: String, data: Map<String, Any?>): Claim = Claim(
            id = id,
            itemId = data["itemId"] as? String ?: "",
            itemOwnerId = data["itemOwnerId"] as? String ?: "",
            claimantId = data["claimantId"] as? String ?: "",
            kind = enumValueOrDefault(data["kind"], ClaimKind.FOUND_IT),
            note = data["note"] as? String ?: "",
            status = enumValueOrDefault(data["status"], ClaimStatus.PENDING),
            conversationId = data["conversationId"] as? String ?: "",
            createdAt = (data["createdAt"] as? Number)?.toLong() ?: 0L,
            updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
        )

        private inline fun <reified T : Enum<T>> enumValueOrDefault(value: Any?, default: T): T =
            enumValues<T>().firstOrNull { it.name == value } ?: default
    }
}

data class ModerationCase(
    @DocumentId val id: String = "",
    val reporterId: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val subjectUserId: String = "",
    val conversationId: String = "",
    val reason: String = "",
    val details: String = "",
    val status: String = "OPEN",
    val context: List<ModerationContextMessage> = emptyList(),
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L
)

data class ModerationContextMessage(
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val mediaType: String = "",
    val mediaUrl: String = "",
    val createdAt: Long = 0L
)
