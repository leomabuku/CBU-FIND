package com.campus.lostandfound.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.campus.lostandfound.data.model.ChatAttachment
import com.campus.lostandfound.data.model.ChatMessage
import com.campus.lostandfound.data.model.Conversation
import com.campus.lostandfound.data.remote.CloudinaryUploader
import com.campus.lostandfound.data.util.ImageCompressor
import com.campus.lostandfound.data.model.Item
import com.campus.lostandfound.data.model.ItemStatus
import com.campus.lostandfound.data.model.ItemType
import com.campus.lostandfound.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.TransactionOptions
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class SyncState(
    val isFromCache: Boolean = true,
    val hasPendingWrites: Boolean = false,
    val error: String? = null
) {
    val isOnline: Boolean get() = !isFromCache && error == null
}

class AppRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val cloudinaryUploader: CloudinaryUploader
) {
    private val usersCollection = firestore.collection("users")
    private val itemsCollection = firestore.collection("items")
    private val conversationsCollection = firestore.collection("conversations")
    private val transactionOptions = TransactionOptions.Builder().setMaxAttempts(1).build()
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    suspend fun registerUser(user: User): String = serverConfirmed("create your profile") {
        val doc = usersCollection.document(user.id)
        firestore.runTransaction(transactionOptions) { transaction ->
            val existing = transaction.get(doc)
            if (!existing.exists()) transaction.set(doc, user)
        }.await()
        user.id
    }

    suspend fun updateUser(user: User) = serverConfirmed("update your profile") {
        val doc = usersCollection.document(user.id)
        firestore.runTransaction(transactionOptions) { transaction ->
            transaction.set(doc, user)
        }.await()
        Unit
    }

    suspend fun getUserById(userId: String): User? = withTimeout(12_000) {
        usersCollection.document(userId).get().await().toObject(User::class.java)
    }

    suspend fun insertItem(item: Item): String = serverConfirmed("publish this report") {
        val doc = itemsCollection.document()
        val newItem = item.copy(id = doc.id)
        firestore.runTransaction(transactionOptions) { transaction -> transaction.set(doc, newItem) }.await()
        doc.id
    }

    suspend fun markItemResolved(itemId: String) = serverConfirmed("update this report") {
        val doc = itemsCollection.document(itemId)
        firestore.runTransaction(transactionOptions) { transaction ->
            transaction.update(
                doc,
                mapOf("status" to ItemStatus.RESOLVED.name, "resolvedAt" to System.currentTimeMillis())
            )
        }.await()
        Unit
    }

    suspend fun uploadImage(uri: Uri, userId: String, folder: String): String = try {
        withTimeout(75_000) {
            val bytes = withContext(Dispatchers.IO) {
                ImageCompressor.compressToJpeg(context, uri)
            }
            cloudinaryUploader.uploadImage(bytes, userId, folder).secureUrl
        }
    } catch (e: Exception) {
        val detail = if (e is TimeoutCancellationException) "The upload timed out." else e.message.orEmpty()
        val guidance = when {
            "not configured" in detail.lowercase() ->
                "Cloudinary is not configured yet."
            "upload preset" in detail.lowercase() ->
                "Confirm your Cloudinary upload preset is unsigned and enabled."
            "cloud" in detail.lowercase() ->
                "Confirm your Cloudinary cloud name."
            else -> "Confirm Cloudinary is reachable and your unsigned upload preset allows image uploads."
        }
        throw IllegalStateException(
            "Image upload failed. $guidance $detail"
        )
    }

    suspend fun uploadChatMedia(uri: Uri, userId: String): ChatAttachment = try {
        withTimeout(90_000) {
            val resolver = context.contentResolver
            val contentType = resolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
            val (fileName, reportedSize) = resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) null else {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    val name = if (nameIndex >= 0) cursor.getString(nameIndex) else null
                    val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                    (name ?: "attachment") to size
                }
            } ?: ("attachment" to -1L)

            require(reportedSize <= CHAT_MEDIA_MAX_BYTES || reportedSize < 0) {
                "Attachments must be 20 MB or smaller."
            }
            val bytes = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "The selected file could not be opened." }
                    val output = java.io.ByteArrayOutputStream()
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= CHAT_MEDIA_MAX_BYTES) { "Attachments must be 20 MB or smaller." }
                        output.write(buffer, 0, read)
                    }
                    output.toByteArray()
                }
            }
            val uploaded = cloudinaryUploader.uploadMedia(bytes, userId, "messages", fileName, contentType)
            ChatAttachment(
                url = uploaded.secureUrl,
                type = when {
                    contentType.startsWith("image/") -> com.campus.lostandfound.data.model.MessageMediaType.IMAGE
                    contentType.startsWith("video/") -> com.campus.lostandfound.data.model.MessageMediaType.VIDEO
                    else -> com.campus.lostandfound.data.model.MessageMediaType.FILE
                },
                name = fileName,
                sizeBytes = bytes.size.toLong()
            )
        }
    } catch (e: Exception) {
        throw IllegalStateException("Media upload failed. ${e.message.orEmpty()}", e)
    }

    fun getConversations(userId: String): Flow<List<Conversation>> = callbackFlow {
        val query = conversationsCollection
            .whereArrayContains("participantIds", userId)
            .orderBy("updatedAt", Query.Direction.DESCENDING)
        val subscription = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                _syncState.value = SyncState(error = readableBackendError(error))
                trySend(emptyList())
            } else if (snapshot != null) {
                _syncState.value = SyncState(
                    isFromCache = snapshot.metadata.isFromCache,
                    hasPendingWrites = snapshot.metadata.hasPendingWrites()
                )
                trySend(snapshot.documents.mapNotNull { it.toObject(Conversation::class.java) })
            }
        }
        awaitClose { subscription.remove() }
    }

    fun getMessages(conversationId: String): Flow<List<ChatMessage>> = callbackFlow {
        val subscription = conversationsCollection.document(conversationId)
            .collection("messages")
            .orderBy("createdAt", Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    _syncState.value = SyncState(error = readableBackendError(error))
                    trySend(emptyList())
                } else if (snapshot != null) {
                    trySend(snapshot.documents.mapNotNull { it.toObject(ChatMessage::class.java) })
                }
            }
        awaitClose { subscription.remove() }
    }

    suspend fun getConversation(conversationId: String): Conversation? = withTimeout(12_000) {
        conversationsCollection.document(conversationId).get().await().toObject(Conversation::class.java)
    }

    suspend fun startConversation(itemId: String, currentUserId: String): String =
        serverConfirmed("start this conversation") {
            val item = getItemById(itemId) ?: error("This report is no longer available.")
            require(item.userId != currentUserId) { "You cannot start a conversation with yourself." }
            val users = listOf(currentUserId, item.userId).sorted()
            val currentUser = getUserById(currentUserId) ?: error("Your profile could not be loaded.")
            val owner = getUserById(item.userId) ?: error("The report owner's profile could not be loaded.")
            val conversationId = "${item.id}_${users[0]}_${users[1]}"
            val doc = conversationsCollection.document(conversationId)
            val now = System.currentTimeMillis()
            val conversation = Conversation(
                id = conversationId,
                participantIds = users,
                participantNames = mapOf(currentUser.id to currentUser.name, owner.id to owner.name),
                participantPhotoUrls = mapOf(currentUser.id to currentUser.photoUrl, owner.id to owner.photoUrl),
                itemId = item.id,
                itemTitle = item.title,
                itemImageUrl = item.imageUrls.firstOrNull() ?: item.imageUri.orEmpty(),
                createdAt = now,
                updatedAt = now,
                lastReadAt = mapOf(currentUserId to now)
            )
            firestore.runTransaction(transactionOptions) { transaction ->
                if (!transaction.get(doc).exists()) transaction.set(doc, conversation)
            }.await()
            conversationId
        }

    suspend fun sendMessage(
        conversationId: String,
        senderId: String,
        text: String,
        attachment: ChatAttachment? = null
    ) = serverConfirmed("send this message") {
        val cleanText = text.trim()
        require(cleanText.isNotBlank() || attachment != null) { "Write a message or add an attachment." }
        require(cleanText.length <= 4000) { "Messages must be 4,000 characters or shorter." }
        val conversationDoc = conversationsCollection.document(conversationId)
        val messageDoc = conversationDoc.collection("messages").document()
        val now = System.currentTimeMillis()
        val message = ChatMessage(
            id = messageDoc.id,
            senderId = senderId,
            text = cleanText,
            mediaUrl = attachment?.url.orEmpty(),
            mediaType = attachment?.type?.name.orEmpty(),
            mediaName = attachment?.name.orEmpty(),
            mediaSizeBytes = attachment?.sizeBytes ?: 0L,
            createdAt = now
        )
        val preview = when {
            cleanText.isNotBlank() -> cleanText.take(160)
            attachment?.type == com.campus.lostandfound.data.model.MessageMediaType.IMAGE -> "Sent a photo"
            attachment?.type == com.campus.lostandfound.data.model.MessageMediaType.VIDEO -> "Sent a video"
            else -> "Sent an attachment"
        }
        firestore.batch()
            .set(messageDoc, message)
            .update(
                conversationDoc,
                mapOf(
                    "updatedAt" to now,
                    "lastMessage" to preview,
                    "lastMessageType" to (attachment?.type?.name ?: "TEXT"),
                    "lastSenderId" to senderId,
                    "lastReadAt.$senderId" to now
                )
            )
            .commit().await()
        Unit
    }

    suspend fun markConversationRead(conversationId: String, userId: String) {
        conversationsCollection.document(conversationId)
            .update(FieldPath.of("lastReadAt", userId), System.currentTimeMillis()).await()
    }

    fun getItemsByType(type: ItemType): Flow<List<Item>> = observeItems(
        itemsCollection.whereEqualTo("type", type.name).orderBy("date", Query.Direction.DESCENDING)
    )

    fun getItemsByUser(userId: String): Flow<List<Item>> = observeItems(
        itemsCollection.whereEqualTo("userId", userId).orderBy("date", Query.Direction.DESCENDING)
    )

    suspend fun getItemById(itemId: String): Item? = withTimeout(12_000) {
        itemsCollection.document(itemId).get().await().toObject(Item::class.java)
    }

    fun searchItems(query: String, type: ItemType): Flow<List<Item>> = getItemsByType(type).map { items ->
        items.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.description.contains(query, ignoreCase = true) ||
                it.category.contains(query, ignoreCase = true) ||
                it.location.contains(query, ignoreCase = true)
        }
    }

    private fun observeItems(query: Query): Flow<List<Item>> = callbackFlow {
        val subscription = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) {
                _syncState.value = SyncState(error = readableBackendError(error))
                // Firestore can reject a listener during sign-out before Compose disposes
                // the previous screen. Surface the state without failing the UI coroutine.
                trySend(emptyList())
                return@addSnapshotListener
            }
            if (snapshot != null) {
                _syncState.value = SyncState(
                    isFromCache = snapshot.metadata.isFromCache,
                    hasPendingWrites = snapshot.metadata.hasPendingWrites()
                )
                trySend(snapshot.documents.mapNotNull { it.toObject(Item::class.java) })
            }
        }
        awaitClose { subscription.remove() }
    }

    private suspend fun <T> serverConfirmed(action: String, block: suspend () -> T): T = try {
        withTimeout(12_000) { block() }
    } catch (e: Exception) {
        val message = if (e is TimeoutCancellationException) {
            "Could not $action because Firebase did not respond. Check that Cloud Firestore is created and your phone has internet access."
        } else {
            "Could not $action. ${readableBackendError(e)}"
        }
        _syncState.value = SyncState(error = message)
        throw IllegalStateException(message, e)
    }

    private fun readableBackendError(error: Exception): String {
        val raw = error.message.orEmpty()
        return when {
            "offline" in raw.lowercase() || "unavailable" in raw.lowercase() ->
                "The app cannot reach Cloud Firestore. Create the database, deploy its rules, and check your connection."
            "permission_denied" in raw.lowercase() || "permission denied" in raw.lowercase() ->
                "Firestore rejected the request. Deploy the project's security rules."
            "index" in raw.lowercase() -> "A required Firestore index is missing. Deploy firestore.indexes.json."
            raw.isNotBlank() -> raw
            else -> "Firebase is currently unavailable."
        }
    }

    private companion object {
        const val CHAT_MEDIA_MAX_BYTES = 20 * 1024 * 1024
    }
}
