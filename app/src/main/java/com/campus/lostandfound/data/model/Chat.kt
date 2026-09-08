package com.campus.lostandfound.data.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.Timestamp

private fun firestoreMillis(value: Any?): Long = when (value) {
    is Number -> value.toLong()
    is Timestamp -> value.toDate().time
    is java.util.Date -> value.time
    else -> 0L
}

private fun stringMap(value: Any?): Map<String, String> =
    (value as? Map<*, *>)?.mapNotNull { (key, entry) ->
        if (key is String && entry is String) key to entry else null
    }?.toMap().orEmpty()

private fun longMap(value: Any?): Map<String, Long> =
    (value as? Map<*, *>)?.mapNotNull { (key, entry) ->
        if (key is String) key to firestoreMillis(entry) else null
    }?.toMap().orEmpty()

enum class MessageMediaType {
    IMAGE, VIDEO, FILE
}

data class ChatAttachment(
    val url: String = "",
    val publicId: String = "",
    val resourceType: String = "auto",
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
    val lastReadAt: Map<String, Long> = emptyMap(),
    val locked: Boolean = false,
    val closed: Boolean = false
) {
    companion object {
        fun fromFirestore(id: String, data: Map<String, Any?>): Conversation = Conversation(
            id = id,
            participantIds = (data["participantIds"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            participantNames = stringMap(data["participantNames"]),
            participantPhotoUrls = stringMap(data["participantPhotoUrls"]),
            itemId = data["itemId"] as? String ?: "",
            itemTitle = data["itemTitle"] as? String ?: "Matched report",
            itemImageUrl = data["itemImageUrl"] as? String ?: "",
            createdAt = firestoreMillis(data["createdAt"]),
            updatedAt = firestoreMillis(data["updatedAt"]),
            lastMessage = data["lastMessage"] as? String ?: "",
            lastMessageType = data["lastMessageType"] as? String ?: "TEXT",
            lastSenderId = data["lastSenderId"] as? String ?: "",
            lastReadAt = longMap(data["lastReadAt"]),
            locked = data["locked"] as? Boolean ?: false,
            closed = data["closed"] as? Boolean ?: false
        )
    }

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
    val mediaPublicId: String = "",
    val mediaResourceType: String = "",
    val mediaType: String = "",
    val mediaName: String = "",
    val mediaSizeBytes: Long = 0L,
    val createdAt: Long = 0L,
    val editedAt: Long = 0L,
    val deleted: Boolean = false,
    val deletedAt: Long = 0L
) {
    companion object {
        fun fromFirestore(id: String, data: Map<String, Any?>): ChatMessage = ChatMessage(
            id = id,
            senderId = data["senderId"] as? String ?: "",
            text = data["text"] as? String ?: "",
            mediaUrl = data["mediaUrl"] as? String ?: "",
            mediaPublicId = data["mediaPublicId"] as? String ?: "",
            mediaResourceType = data["mediaResourceType"] as? String ?: "",
            mediaType = data["mediaType"] as? String ?: "",
            mediaName = data["mediaName"] as? String ?: "",
            mediaSizeBytes = firestoreMillis(data["mediaSizeBytes"]),
            createdAt = firestoreMillis(data["createdAt"]),
            editedAt = firestoreMillis(data["editedAt"]),
            deleted = data["deleted"] as? Boolean ?: false,
            deletedAt = firestoreMillis(data["deletedAt"])
        )
    }
}
