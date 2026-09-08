package com.campus.lostandfound.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.campus.lostandfound.ui.screens.EmptyFeatureState
import com.campus.lostandfound.ui.theme.CampusLostAndFoundTheme
import org.junit.Rule
import org.junit.Test

class EmptyStateTest {
    @get:Rule val compose = createComposeRule()

    @Test fun claimEmptyStateExplainsWhatHappensNext() {
        compose.setContent { CampusLostAndFoundTheme { EmptyFeatureState("No claims yet", "Claims you submit appear here.") } }
        compose.onNodeWithText("No claims yet").assertIsDisplayed()
        compose.onNodeWithText("Claims you submit appear here.").assertIsDisplayed()
    }
}
