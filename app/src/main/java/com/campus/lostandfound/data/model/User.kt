package com.campus.lostandfound.data.model

import com.google.firebase.firestore.DocumentId

data class User(
    @DocumentId
    val id: String = "",
    val name: String = "",
    val studentId: String = "",
    val email: String = "",
    val programme: String = "",
    val yearOfStudy: String = "",
    val phone: String = "",
    val photoUrl: String = "",
    val createdAt: Long = 0L
)
