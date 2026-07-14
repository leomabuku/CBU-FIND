package com.campus.lostandfound.data.model

import com.google.firebase.firestore.DocumentId

enum class ItemType {
    LOST, FOUND
}

enum class ItemStatus {
    ACTIVE, RESOLVED
}

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
    val date: Long = 0L,
    val status: ItemStatus = ItemStatus.ACTIVE,
    val userId: String = "",
    val contactInfo: String = "",
    val resolvedAt: Long? = null
)
