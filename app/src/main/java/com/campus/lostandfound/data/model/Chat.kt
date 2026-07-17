package com.campus.lostandfound.data.model

import com.google.firebase.firestore.DocumentId

enum class MessageMediaType {
    IMAGE, VIDEO, FILE
}

data class ChatAttachment(
    val url: String = "",
    val type: MessageMediaType = MessageMediaType.FILE,
    val name: String = "",
    val sizeBytes: Long = 0L
)

data class Conversation(
    @DocumentId
    val id: String = "",
    val participantIds: List<String> = emptyList(),
    val participantNames: Map<String, String> = emptyMap(),
    val participantPhotoUrls: Map<String, String> = emptyMap(),
    val itemId: String = "",
    val itemTitle: String = "",
    val itemImageUrl: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val lastMessage: String = "",
    val lastMessageType: String = "TEXT",
    val lastSenderId: String = "",
    val lastReadAt: Map<String, Long> = emptyMap()
) {
    fun otherParticipantId(currentUserId: String): String =
        participantIds.firstOrNull { it != currentUserId }.orEmpty()

    fun displayName(currentUserId: String): String =
        participantNames[otherParticipantId(currentUserId)] ?: "Campus member"

    fun photoUrl(currentUserId: String): String =
        participantPhotoUrls[otherParticipantId(currentUserId)].orEmpty()

    fun isUnread(currentUserId: String): Boolean =
        lastSenderId.isNotBlank() &&
            lastSenderId != currentUserId &&
            updatedAt > (lastReadAt[currentUserId] ?: 0L)
}

data class ChatMessage(
    @DocumentId
    val id: String = "",
    val senderId: String = "",
    val text: String = "",
    val mediaUrl: String = "",
    val mediaType: String = "",
    val mediaName: String = "",
    val mediaSizeBytes: Long = 0L,
    val createdAt: Long = 0L
)
