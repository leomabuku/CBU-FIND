package com.campus.lostandfound.data.repository

import android.content.Context
import android.net.Uri
import com.campus.lostandfound.data.remote.CloudinaryUploader
import com.campus.lostandfound.data.util.ImageCompressor
import com.campus.lostandfound.data.model.Item
import com.campus.lostandfound.data.model.ItemStatus
import com.campus.lostandfound.data.model.ItemType
import com.campus.lostandfound.data.model.User
import com.google.firebase.firestore.FirebaseFirestore
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
}
