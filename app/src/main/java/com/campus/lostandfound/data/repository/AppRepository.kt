package com.campus.lostandfound.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.campus.lostandfound.data.model.ChatAttachment
import com.campus.lostandfound.data.model.ChatMessage
import com.campus.lostandfound.data.model.Claim
import com.campus.lostandfound.data.model.ClaimStatus
import com.campus.lostandfound.data.model.Conversation
import com.campus.lostandfound.data.model.Item
import com.campus.lostandfound.data.model.ItemStatus
import com.campus.lostandfound.data.model.ItemType
import com.campus.lostandfound.data.model.MediaAsset
import com.campus.lostandfound.data.model.MessageMediaType
import com.campus.lostandfound.data.model.ModerationCase
import com.campus.lostandfound.data.model.User
import com.campus.lostandfound.data.remote.ApiFailure
import com.campus.lostandfound.data.remote.CloudinaryUploader
import com.campus.lostandfound.data.remote.WorkerApiClient
import com.campus.lostandfound.data.util.ImageCompressor
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

data class SyncState(
    val isLoading: Boolean = true,
    val isFromCache: Boolean = true,
    val hasPendingWrites: Boolean = false,
    val error: String? = null,
    val errorCode: String? = null,
    val referenceId: String? = null
) {
    val isOnline: Boolean get() = !isLoading && !isFromCache && error == null
}

data class ClaimActionResult(val status: ClaimStatus, val conversationId: String? = null)

class AppRepository(
    private val context: Context,
    private val firestore: FirebaseFirestore,
    private val cloudinaryUploader: CloudinaryUploader,
    private val api: WorkerApiClient
) {
    private val users = firestore.collection("users")
    private val items = firestore.collection("items")
    private val conversations = firestore.collection("conversations")
    private val claims = firestore.collection("claims")
    private val moderationCases = firestore.collection("moderationCases")
    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    suspend fun registerUser(user: User): String = trusted("create your profile") {
        api.request("/v1/profile/bootstrap", body = user.profileJson())
        user.id
    }

    suspend fun updateUser(user: User) = trusted("update your profile") {
        api.request("/v1/profile", "PATCH", user.profileJson())
        Unit
    }

    suspend fun getUserById(userId: String): User? = withTimeout(12_000) {
        users.document(userId).get().await().toObject(User::class.java)
    }

    suspend fun insertItem(item: Item): String = trusted("publish this report") {
        api.request("/v1/reports", body = item.reportJson()).getString("id")
    }

    suspend fun markItemResolved(itemId: String) = trusted("update this report") {
        api.request("/v1/reports/$itemId/status", body = JSONObject().put("status", ItemStatus.RESOLVED.name))
        Unit
    }

    suspend fun reopenItem(itemId: String) = trusted("reopen this report") {
        api.request("/v1/reports/$itemId/status", body = JSONObject().put("status", ItemStatus.ACTIVE.name))
        Unit
    }

    suspend fun createClaim(item: Item, note: String = ""): String = trusted("submit your claim") {
        api.request(
            "/v1/claims",
            body = JSONObject().put("itemId", item.id).put("kind", if (item.type == ItemType.LOST) "FOUND_IT" else "THIS_IS_MINE").put("note", note.trim())
        ).getString("id")
    }

    suspend fun actOnClaim(claimId: String, action: String): ClaimActionResult = trusted("update this claim") {
        val result = api.request("/v1/claims/$claimId/action", body = JSONObject().put("action", action))
        val status = runCatching { ClaimStatus.valueOf(result.getString("status")) }
            .getOrElse { throw IllegalStateException("The server returned an invalid claim status. Reference: ANDROID-CLAIM-STATUS") }
        ClaimActionResult(status, result.optString("conversationId").ifBlank { null })
    }

    fun getClaims(userId: String, onSync: (SyncState) -> Unit = {}): Flow<List<Claim>> = callbackFlow {
        publishSync(SyncState(isLoading = true), onSync)
        var incoming: List<Claim> = emptyList()
        var outgoing: List<Claim> = emptyList()
        var incomingReady = false
        var outgoingReady = false
        var incomingFromCache = true
        var outgoingFromCache = true
        var incomingPending = false
        var outgoingPending = false
        var terminalError: SyncState? = null
        fun publish() { trySend((incoming + outgoing).distinctBy { it.id }.sortedByDescending { it.updatedAt }) }
        fun publishState() {
            val state = terminalError ?: if (incomingReady && outgoingReady) SyncState(isLoading = false, isFromCache = incomingFromCache || outgoingFromCache, hasPendingWrites = incomingPending || outgoingPending) else SyncState(isLoading = true)
            publishSync(state, onSync)
        }
        val incomingListener = claims.whereEqualTo("itemOwnerId", userId).orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error -> if (error != null) { terminalError = syncFailure(error); publishState() } else if (snapshot != null) { incoming = snapshot.documents.map { Claim.fromFirestore(it.id, it.data.orEmpty()) }; incomingReady = true; incomingFromCache = snapshot.metadata.isFromCache; incomingPending = snapshot.metadata.hasPendingWrites(); publishState(); publish() } }
        val outgoingListener = claims.whereEqualTo("claimantId", userId).orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error -> if (error != null) { terminalError = syncFailure(error); publishState() } else if (snapshot != null) { outgoing = snapshot.documents.map { Claim.fromFirestore(it.id, it.data.orEmpty()) }; outgoingReady = true; outgoingFromCache = snapshot.metadata.isFromCache; outgoingPending = snapshot.metadata.hasPendingWrites(); publishState(); publish() } }
        awaitClose { incomingListener.remove(); outgoingListener.remove() }
    }

    suspend fun uploadImage(uri: Uri, folder: String): MediaAsset = try {
        withTimeout(75_000) {
            val bytes = withContext(Dispatchers.IO) { ImageCompressor.compressToJpeg(context, uri) }
            cloudinaryUploader.uploadImage(bytes, folder).toMediaAsset()
        }
    } catch (error: Exception) {
        throw uploadFailure(error)
    }

    suspend fun uploadChatMedia(uri: Uri): ChatAttachment = try {
        withTimeout(90_000) {
            val resolver = context.contentResolver
            val contentType = resolver.getType(uri).orEmpty().ifBlank { "application/octet-stream" }
            val (fileName, reportedSize) = resolver.query(uri, null, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) null else {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    (if (nameIndex >= 0) cursor.getString(nameIndex) else "attachment") to (if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L)
                }
            } ?: ("attachment" to -1L)
            require(reportedSize <= CHAT_MEDIA_MAX_BYTES || reportedSize < 0) { "Attachments must be 20 MB or smaller." }
            val bytes = withContext(Dispatchers.IO) {
                resolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "The selected file could not be opened." }
                    val output = java.io.ByteArrayOutputStream(); val buffer = ByteArray(DEFAULT_BUFFER_SIZE); var total = 0
                    while (true) { val read = input.read(buffer); if (read < 0) break; total += read; require(total <= CHAT_MEDIA_MAX_BYTES) { "Attachments must be 20 MB or smaller." }; output.write(buffer, 0, read) }
                    output.toByteArray()
                }
            }
            val uploaded = cloudinaryUploader.uploadMedia(bytes, "messages", fileName, contentType)
            ChatAttachment(
                url = uploaded.secureUrl, publicId = uploaded.publicId, resourceType = uploaded.resourceType,
                type = when { contentType.startsWith("image/") -> MessageMediaType.IMAGE; contentType.startsWith("video/") -> MessageMediaType.VIDEO; else -> MessageMediaType.FILE },
                name = fileName, sizeBytes = bytes.size.toLong()
            )
        }
    } catch (error: Exception) { throw uploadFailure(error) }

    fun getConversations(userId: String, onSync: (SyncState) -> Unit = {}): Flow<List<Conversation>> = callbackFlow {
        publishSync(SyncState(isLoading = true), onSync)
        val listener = conversations.whereArrayContains("participantIds", userId).orderBy("updatedAt", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) listenerFailed(error, onSync)
                else if (snapshot != null) { listenerReady(snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites(), onSync); trySend(snapshot.documents.map { Conversation.fromFirestore(it.id, it.data.orEmpty()) }) }
            }
        awaitClose { listener.remove() }
    }

    fun getMessages(conversationId: String, onSync: (SyncState) -> Unit = {}): Flow<List<ChatMessage>> = callbackFlow {
        publishSync(SyncState(isLoading = true), onSync)
        val listener = conversations.document(conversationId).collection("messages").orderBy("createdAt")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) listenerFailed(error, onSync)
                else if (snapshot != null) { listenerReady(snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites(), onSync); trySend(snapshot.documents.map { ChatMessage.fromFirestore(it.id, it.data.orEmpty()) }) }
            }
        awaitClose { listener.remove() }
    }

    suspend fun getConversation(conversationId: String): Conversation? = withTimeout(12_000) {
        conversations.document(conversationId).get().await().let { snapshot ->
            if (!snapshot.exists()) null else Conversation.fromFirestore(snapshot.id, snapshot.data.orEmpty())
        }
    }

    suspend fun sendMessage(conversationId: String, text: String, attachment: ChatAttachment? = null) = trusted("send this message") {
        val attachmentJson = attachment?.let {
            JSONObject().put("secureUrl", it.url).put("publicId", it.publicId).put("resourceType", it.resourceType)
                .put("type", it.type.name).put("name", it.name).put("bytes", it.sizeBytes)
        }
        api.request("/v1/conversations/$conversationId/messages", body = JSONObject().put("text", text.trim()).put("attachment", attachmentJson))
        Unit
    }

    suspend fun editMessage(conversationId: String, messageId: String, text: String) = trusted("edit this message") {
        api.request("/v1/conversations/$conversationId/messages/$messageId", "PATCH", JSONObject().put("text", text.trim()))
        Unit
    }

    suspend fun deleteMessage(conversationId: String, messageId: String) = trusted("delete this message") {
        api.request("/v1/conversations/$conversationId/messages/$messageId", "DELETE")
        Unit
    }

    suspend fun markConversationRead(conversationId: String) = trusted("mark this conversation read") {
        api.request("/v1/conversations/$conversationId/read")
        Unit
    }

    fun getItemsByType(type: ItemType, onSync: (SyncState) -> Unit = {}): Flow<List<Item>> = observeItems(items.whereEqualTo("type", type.name).whereIn("status", VISIBLE_ITEM_STATUSES).orderBy("date", Query.Direction.DESCENDING), onSync)
    fun getItemsByUser(userId: String, onSync: (SyncState) -> Unit = {}): Flow<List<Item>> = observeItems(items.whereEqualTo("userId", userId).whereIn("status", VISIBLE_ITEM_STATUSES).orderBy("date", Query.Direction.DESCENDING), onSync)
    suspend fun getItemById(itemId: String): Item? = withTimeout(12_000) { items.document(itemId).get().await().toObject(Item::class.java) }
    suspend fun getReportContact(itemId: String): String = trusted("load the private contact details") {
        api.request("/v1/reports/$itemId/contact", "GET").optString("contactInfo").ifBlank { "No private contact details were supplied." }
    }
    fun searchItems(query: String, type: ItemType, onSync: (SyncState) -> Unit = {}): Flow<List<Item>> = getItemsByType(type, onSync).map { results -> results.filter { listOf(it.title, it.description, it.category, it.location).any { value -> value.contains(query, true) } } }

    suspend fun block(targetUid: String) = trusted("block this account") { api.request("/v1/blocks", body = JSONObject().put("targetUid", targetUid)); Unit }
    suspend fun unblock(targetUid: String) = trusted("unblock this account") { api.request("/v1/blocks/$targetUid", "DELETE"); Unit }
    suspend fun getBlockedAccounts(): List<String> = trusted("load blocked accounts") {
        val values = api.request("/v1/blocks", "GET").optJSONArray("targetUids") ?: JSONArray()
        List(values.length()) { index -> values.optString(index) }.filter { it.isNotBlank() }
    }
    suspend fun requestAccountDeletion(): String = trusted("queue account deletion") { api.request("/v1/account-deletion").getString("referenceId") }

    suspend fun saveNotificationPreferences(preferences: Map<String, Boolean>) = trusted("save notification preferences") {
        val json = JSONObject(); preferences.forEach(json::put); api.request("/v1/notification-preferences", "PUT", json); Unit
    }

    suspend fun getNotificationPreferences(): Map<String, Boolean> = trusted("load notification preferences") {
        val data = api.request("/v1/notification-preferences", "GET")
        NOTIFICATION_PREFERENCE_KEYS.associateWith { key -> data.optBoolean(key, key != "showMessagePreview") }
    }

    suspend fun registerDeviceToken(token: String) = trusted("enable push notifications") {
        api.request("/v1/devices", "PUT", JSONObject().put("token", token).put("platform", "ANDROID")); Unit
    }

    suspend fun getRole(uid: String): String = firestore.collection("roles").document(uid).get().await().getString("role") ?: "USER"

    fun getModerationCases(onSync: (SyncState) -> Unit = {}): Flow<List<ModerationCase>> = callbackFlow {
        publishSync(SyncState(isLoading = true), onSync)
        val listener = moderationCases.orderBy("updatedAt", Query.Direction.DESCENDING).limit(100).addSnapshotListener { snapshot, error ->
            if (error != null) listenerFailed(error, onSync) else if (snapshot != null) { listenerReady(snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites(), onSync); trySend(snapshot.documents.mapNotNull { it.toObject(ModerationCase::class.java) }) }
        }
        awaitClose { listener.remove() }
    }

    suspend fun moderationAction(caseId: String, action: String, targetId: String) = trusted("apply this moderation action") {
        api.request("/v1/admin/cases/$caseId/action", body = JSONObject().put("action", action).put("targetId", targetId)); Unit
    }

    suspend fun assignRole(uid: String, role: String) = trusted("update this role") {
        api.request("/v1/admin/roles/$uid", "PUT", JSONObject().put("role", role)); Unit
    }

    suspend fun reportAbuse(targetType: String, targetId: String, conversationId: String = "", reason: String, details: String) = trusted("submit this abuse report") {
        api.request("/v1/abuse-reports", body = JSONObject().put("targetType", targetType).put("targetId", targetId).put("conversationId", conversationId).put("reason", reason).put("details", details)); Unit
    }

    private fun observeItems(query: Query, onSync: (SyncState) -> Unit): Flow<List<Item>> = callbackFlow {
        publishSync(SyncState(isLoading = true), onSync)
        val listener = query.addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
            if (error != null) listenerFailed(error, onSync)
            else if (snapshot != null) { listenerReady(snapshot.metadata.isFromCache, snapshot.metadata.hasPendingWrites(), onSync); trySend(snapshot.documents.mapNotNull { it.toObject(Item::class.java) }.filter { it.status != ItemStatus.REMOVED }) }
        }
        awaitClose { listener.remove() }
    }

    private fun listenerReady(fromCache: Boolean, pending: Boolean, onSync: (SyncState) -> Unit) { publishSync(SyncState(isLoading = false, isFromCache = fromCache, hasPendingWrites = pending), onSync) }
    private fun listenerFailed(error: Exception, onSync: (SyncState) -> Unit) { publishSync(syncFailure(error), onSync) }
    private fun syncFailure(error: Exception) = SyncState(isLoading = false, error = readableFirestoreError(error), errorCode = "FIRESTORE_LISTENER", referenceId = "ANDROID-FIRESTORE")
    private fun publishSync(state: SyncState, onSync: (SyncState) -> Unit) { _syncState.value = state; onSync(state) }

    private suspend fun <T> trusted(action: String, block: suspend () -> T): T = try { withTimeout(35_000) { block() } }
    catch (error: Exception) {
        val failure = when (error) {
            is ApiFailure -> error
            is TimeoutCancellationException -> ApiFailure("DEPENDENCY_UNAVAILABLE", "Could not $action because the service did not respond. Check your connection and retry.", true, "ANDROID-TIMEOUT")
            else -> ApiFailure("INTERNAL_ERROR", "Could not $action. Please retry.", true, "ANDROID-UNEXPECTED", error)
        }
        _syncState.value = SyncState(isLoading = false, error = failure.userMessage(), errorCode = failure.code, referenceId = failure.referenceId)
        FirebaseCrashlytics.getInstance().apply { setCustomKey("error_code", failure.code); setCustomKey("reference_id", failure.referenceId); recordException(failure) }
        throw IllegalStateException(failure.userMessage(), failure)
    }

    private fun readableFirestoreError(error: Exception): String {
        val raw = error.message.orEmpty().lowercase()
        return when {
            "permission" in raw -> "Your session cannot access this information. Sign in again. Reference: ANDROID-FIRESTORE-PERMISSION"
            "index" in raw -> "This view is being prepared. Ask an administrator to deploy the required index. Reference: ANDROID-FIRESTORE-INDEX"
            "unavailable" in raw || "offline" in raw -> "CBU Find is offline. Check your connection and retry. Reference: ANDROID-FIRESTORE-OFFLINE"
            else -> "CBU Find could not load the latest information. Retry the view. Reference: ANDROID-FIRESTORE"
        }
    }

    private fun uploadFailure(error: Exception): IllegalStateException {
        val message = if (error is TimeoutCancellationException) "The upload timed out. Check your connection and retry. Reference: ANDROID-UPLOAD-TIMEOUT" else if (error is ApiFailure) error.userMessage() else "The media upload failed. Retry, or continue without an attachment. Reference: ANDROID-UPLOAD"
        return IllegalStateException(message, error)
    }

    private fun User.profileJson() = JSONObject().put("name", name.trim()).put("studentId", studentId.trim()).put("programme", programme.trim()).put("yearOfStudy", yearOfStudy.trim()).put("phone", phone.trim()).put("photoUrl", photoUrl.trim()).apply { photoAsset?.let { put("photoAsset", it.json()) } }
    private fun Item.reportJson(): JSONObject {
        val assets = JSONArray(); media.forEach { asset -> assets.put(asset.json()) }
        return JSONObject().put("type", type.name).put("title", title.trim()).put("description", description.trim()).put("category", category).put("location", location.trim()).put("date", date).put("contactInfo", contactInfo.trim()).put("media", assets)
    }
    private fun MediaAsset.json() = JSONObject().put("secureUrl", secureUrl).put("publicId", publicId).put("resourceType", resourceType).put("format", format).put("bytes", bytes).put("width", width).put("height", height).put("originalName", originalName)
    private fun com.campus.lostandfound.data.remote.CloudinaryUploadResult.toMediaAsset() = MediaAsset(secureUrl, publicId, resourceType, format, bytes, width, height, originalName)

    private companion object {
        const val CHAT_MEDIA_MAX_BYTES = 20 * 1024 * 1024
        val NOTIFICATION_PREFERENCE_KEYS = listOf("claims", "claimDecisions", "messages", "reportUpdates", "moderation", "showMessagePreview")
        val VISIBLE_ITEM_STATUSES = listOf(ItemStatus.ACTIVE.name, ItemStatus.MATCHED.name, ItemStatus.RESOLVED.name)
    }
}
