package com.campus.lostandfound.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.campus.lostandfound.CampusLostAndFoundApp
import com.campus.lostandfound.ui.viewmodel.AuthViewModel
import com.campus.lostandfound.ui.viewmodel.CreateItemViewModel
import com.campus.lostandfound.ui.viewmodel.HomeViewModel
import com.campus.lostandfound.ui.viewmodel.InboxViewModel
import com.campus.lostandfound.ui.viewmodel.ChatViewModel
import com.campus.lostandfound.ui.viewmodel.ItemDetailsViewModel
import com.campus.lostandfound.ui.viewmodel.ClaimsViewModel
import com.campus.lostandfound.ui.viewmodel.SettingsViewModel
import com.campus.lostandfound.ui.viewmodel.ModerationViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            AuthViewModel(campusApplication().container.auth, campusApplication().container.repository)
        }
        initializer {
            HomeViewModel(campusApplication().container.repository)
        }
        initializer {
            CreateItemViewModel(campusApplication().container.repository)
        }
        initializer {
            ItemDetailsViewModel(campusApplication().container.repository)
        }
        initializer {
            InboxViewModel(campusApplication().container.repository)
        }
        initializer {
            ChatViewModel(campusApplication().container.repository)
        }
        initializer { ClaimsViewModel(campusApplication().container.repository) }
        initializer { SettingsViewModel(campusApplication().container.repository) }
        initializer { ModerationViewModel(campusApplication().container.repository) }
        initializer {
            com.campus.lostandfound.ui.viewmodel.ProfileViewModel(campusApplication().container.repository)
        }
    }
}

fun CreationExtras.campusApplication(): CampusLostAndFoundApp =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as CampusLostAndFoundApp)
