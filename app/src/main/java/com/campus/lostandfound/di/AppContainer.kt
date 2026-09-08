package com.campus.lostandfound.di

import android.content.Context
import com.campus.lostandfound.data.remote.CloudinaryUploader
import com.campus.lostandfound.data.remote.WorkerApiClient
import com.campus.lostandfound.data.repository.AppRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AppContainer(private val context: Context) {
    val auth: FirebaseAuth by lazy {
        Firebase.auth
    }
    
    val firestore: FirebaseFirestore by lazy {
        Firebase.firestore
    }

    val workerApi: WorkerApiClient by lazy {
        WorkerApiClient(auth, FirebaseAppCheck.getInstance())
    }

    val cloudinaryUploader: CloudinaryUploader by lazy {
        CloudinaryUploader(workerApi)
    }

    val repository: AppRepository by lazy {
        AppRepository(context, firestore, cloudinaryUploader, workerApi)
    }
}
