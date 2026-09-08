package com.campus.lostandfound.data.model

import com.google.firebase.firestore.DocumentId

enum class ItemType {
    LOST, FOUND
}

enum class ItemStatus {
    ACTIVE, MATCHED, RESOLVED, REMOVED
}

data class MediaAsset(
    val secureUrl: String = "",
    val publicId: String = "",
    val resourceType: String = "image",
    val format: String = "",
    val bytes: Long = 0L,
    val width: Int = 0,
    val height: Int = 0,
    val originalName: String = ""
)

object ItemCategories {
    val all = listOf(
        "Student ID & Documents",
        "Phones & Electronics",
        "Keys",
        "Bags & Luggage",
        "Clothing",
        "Books & Stationery",
        "Bank Cards & Money",
        "Jewellery & Accessories",
        "Other"
    )
}

data class Item(
    @DocumentId
    val id: String = "",
    val type: ItemType = ItemType.LOST,
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val location: String = "",
    val imageUri: String? = null,
    val imageUrls: List<String> = emptyList(),
    val media: List<MediaAsset> = emptyList(),
    val date: Long = 0L,
    val status: ItemStatus = ItemStatus.ACTIVE,
    val userId: String = "",
    val contactInfo: String = "",
    val matchedClaimId: String = "",
    val resolvedAt: Long? = null,
    val updatedAt: Long = 0L
)
