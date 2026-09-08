package com.campus.lostandfound.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.campus.lostandfound.ui.screens.AuthScreen
import com.campus.lostandfound.ui.screens.CreateListingScreen
import com.campus.lostandfound.ui.screens.HomeDashboardScreen
import com.campus.lostandfound.ui.screens.ItemDetailsScreen
import com.campus.lostandfound.ui.screens.InboxScreen
import com.campus.lostandfound.ui.screens.ChatScreen
import com.campus.lostandfound.ui.screens.SplashScreen
import com.campus.lostandfound.ui.viewmodel.AuthViewModel
import com.campus.lostandfound.ui.viewmodel.CreateItemViewModel
import com.campus.lostandfound.ui.viewmodel.HomeViewModel
import com.campus.lostandfound.ui.viewmodel.ItemDetailsViewModel
import com.campus.lostandfound.ui.viewmodel.InboxViewModel
import com.campus.lostandfound.ui.viewmodel.ChatViewModel
import com.campus.lostandfound.ui.viewmodel.ClaimsViewModel
import com.campus.lostandfound.ui.viewmodel.SettingsViewModel
import com.campus.lostandfound.ui.viewmodel.ModerationViewModel
import com.campus.lostandfound.ui.screens.ClaimsScreen
import com.campus.lostandfound.ui.screens.SettingsScreen
import com.campus.lostandfound.ui.screens.ModerationScreen
import com.campus.lostandfound.ui.theme.ThemeMode

@Composable
fun CampusApp(
    notificationDestination: String? = null,
    onNotificationDestinationConsumed: () -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChanged: (ThemeMode) -> Unit = {}
) {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val isAuthReady by authViewModel.isAuthReady.collectAsStateWithLifecycle()

    LaunchedEffect(notificationDestination, isAuthReady, currentUser?.id) {
        if (notificationDestination != null && isAuthReady && currentUser != null) {
            val currentRoute = navController.currentDestination?.route
            if (currentRoute != "splash" && currentRoute != "auth") {
                navController.navigate(notificationDestination) { launchSingleTop = true }
                onNotificationDestinationConsumed()
            }
        }
    }

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(isReady = isAuthReady, onTimeout = {
                navController.navigate(if (currentUser == null) "auth" else notificationDestination ?: "home") {
                    popUpTo("splash") { inclusive = true }
                }
                if (currentUser != null && notificationDestination != null) onNotificationDestinationConsumed()
            })
        }
        composable("auth") {
            AuthScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate(notificationDestination ?: "home") {
                        popUpTo("auth") { inclusive = true }
                    }
                    if (notificationDestination != null) onNotificationDestinationConsumed()
                }
            )
        }
        composable("home") {
            val homeViewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
            HomeDashboardScreen(
                viewModel = homeViewModel,
                currentUserId = currentUser?.id ?: "",
                onCreateListing = { navController.navigate("create_listing") },
                onItemClick = { itemId -> navController.navigate("item_details/$itemId") },
                onProfileClick = { navController.navigate("profile") },
                onInboxClick = { navController.navigate("inbox") },
                onClaimsClick = { navController.navigate("claims") },
                onSettingsClick = { navController.navigate("settings") },
                onModerationClick = { navController.navigate("moderation") }
            )
        }
        composable("create_listing") {
            val createItemViewModel: CreateItemViewModel = viewModel(factory = AppViewModelProvider.Factory)
            CreateListingScreen(
                viewModel = createItemViewModel,
                userId = currentUser?.id ?: "",
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(
            route = "item_details/{itemId}",
            arguments = listOf(navArgument("itemId") { type = NavType.StringType })
        ) { backStackEntry ->
            val itemId = backStackEntry.arguments?.getString("itemId") ?: ""
            val itemDetailsViewModel: ItemDetailsViewModel = viewModel(factory = AppViewModelProvider.Factory)
            ItemDetailsScreen(
                itemId = itemId,
                currentUserId = currentUser?.id ?: "",
                viewModel = itemDetailsViewModel,
                onNavigateBack = { navController.popBackStack() },
                onClaimSubmitted = { navController.navigate("claims") }
            )
        }
        composable("claims") {
            val claimsViewModel: ClaimsViewModel = viewModel(factory = AppViewModelProvider.Factory)
            ClaimsScreen(currentUser?.id ?: "", claimsViewModel, { navController.popBackStack() }) { conversationId -> navController.navigate("chat/$conversationId") }
        }
        composable("settings") {
            val settingsViewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
            SettingsScreen(settingsViewModel, themeMode, onThemeModeChanged, { navController.popBackStack() }) {
                authViewModel.logout()
                navController.navigate("auth") { popUpTo("home") { inclusive = true } }
            }
        }
        composable("moderation") {
            val moderationViewModel: ModerationViewModel = viewModel(factory = AppViewModelProvider.Factory)
            ModerationScreen(currentUser?.id ?: "", moderationViewModel) { navController.popBackStack() }
        }
        composable("inbox") {
            val inboxViewModel: InboxViewModel = viewModel(factory = AppViewModelProvider.Factory)
            InboxScreen(
                currentUserId = currentUser?.id ?: "",
                viewModel = inboxViewModel,
                onNavigateBack = { navController.popBackStack() },
                onConversationClick = { conversationId -> navController.navigate("chat/$conversationId") }
            )
        }
        composable(
            route = "chat/{conversationId}",
            arguments = listOf(navArgument("conversationId") { type = NavType.StringType })
        ) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            val chatViewModel: ChatViewModel = viewModel(factory = AppViewModelProvider.Factory)
            ChatScreen(
                conversationId = conversationId,
                currentUserId = currentUser?.id ?: "",
                viewModel = chatViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable("profile") {
            val profileViewModel: com.campus.lostandfound.ui.viewmodel.ProfileViewModel = viewModel(factory = AppViewModelProvider.Factory)
            com.campus.lostandfound.ui.screens.ProfileScreen(
                user = currentUser ?: return@composable,
                viewModel = profileViewModel,
                onLogout = {
                    authViewModel.logout()
                    navController.navigate("auth") {
                        popUpTo("home") { inclusive = true }
                    }
                },
                onNavigateBack = { navController.popBackStack() },
                onProfileUpdated = authViewModel::refreshProfile,
                onItemClick = { itemId -> navController.navigate("item_details/$itemId") }
            )
        }
    }
}
