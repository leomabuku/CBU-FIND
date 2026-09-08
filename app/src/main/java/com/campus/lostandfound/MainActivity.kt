package com.campus.lostandfound

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.campus.lostandfound.ui.theme.CampusLostAndFoundTheme
import com.campus.lostandfound.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val notificationDestination = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationDestination.value = destinationFrom(intent)
        enableEdgeToEdge()
        setContent {
            val destination = notificationDestination.collectAsStateWithLifecycle().value
            val themePreferences = remember { getSharedPreferences(THEME_PREFERENCES, MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(ThemeMode.fromStored(themePreferences.getString(THEME_MODE, null))) }
            val systemDark = isSystemInDarkTheme()
            val darkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> systemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            CampusLostAndFoundTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    com.campus.lostandfound.ui.CampusApp(
                        notificationDestination = destination,
                        onNotificationDestinationConsumed = { notificationDestination.value = null },
                        themeMode = themeMode,
                        onThemeModeChanged = { selected ->
                            themeMode = selected
                            themePreferences.edit().putString(THEME_MODE, selected.name).apply()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        notificationDestination.value = destinationFrom(intent)
    }

    private fun destinationFrom(intent: Intent?): String? {
        val conversationId = intent?.getStringExtra(EXTRA_CONVERSATION_ID)?.takeIf(String::isNotBlank)
        if (conversationId != null) return "chat/${Uri.encode(conversationId)}"
        if (!intent?.getStringExtra(EXTRA_CLAIM_ID).isNullOrBlank()) return "claims"
        val itemId = intent?.getStringExtra(EXTRA_ITEM_ID)?.takeIf(String::isNotBlank)
        return itemId?.let { "item_details/${Uri.encode(it)}" }
    }

    companion object {
        const val EXTRA_CONVERSATION_ID = "cbu_find.conversation_id"
        const val EXTRA_CLAIM_ID = "cbu_find.claim_id"
        const val EXTRA_ITEM_ID = "cbu_find.item_id"
        private const val THEME_PREFERENCES = "cbu_find.appearance"
        private const val THEME_MODE = "theme_mode"
    }
}
