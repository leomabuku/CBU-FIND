package com.campus.lostandfound

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

internal object AppCheckInstaller {
    fun install() {
        val firebaseApp = FirebaseApp.getInstance()
        val debugSecret = BuildConfig.APP_CHECK_DEBUG_SECRET.trim()
        if (debugSecret.isNotBlank()) {
            val preferencesName = "com.google.firebase.appcheck.debug.store.${firebaseApp.persistenceKey}"
            firebaseApp.applicationContext
                .getSharedPreferences(preferencesName, android.content.Context.MODE_PRIVATE)
                .edit()
                .putString("com.google.firebase.appcheck.debug.DEBUG_SECRET", debugSecret)
                .apply()
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )
    }
}
