package com.campus.lostandfound.ui

import androidx.compose.runtime.Composable
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

@Composable
fun CampusApp() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel(factory = AppViewModelProvider.Factory)
    val currentUser by authViewModel.currentUser.collectAsStateWithLifecycle()
    val isAuthReady by authViewModel.isAuthReady.collectAsStateWithLifecycle()

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen(isReady = isAuthReady, onTimeout = {
                navController.navigate(if (currentUser == null) "auth" else "home") {
                    popUpTo("splash") { inclusive = true }
                }
            })
        }
        composable("auth") {
            AuthScreen(
                viewModel = authViewModel,
                onLoginSuccess = {
                    navController.navigate("home") {
                        popUpTo("auth") { inclusive = true }
                    }
                }
            )
        }
        composable("home") {
            val homeViewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
            HomeDashboardScreen(
                viewModel = homeViewModel,
                onCreateListing = { navController.navigate("create_listing") },
                onItemClick = { itemId -> navController.navigate("item_details/$itemId") },
                onProfileClick = { navController.navigate("profile") },
                onInboxClick = { navController.navigate("inbox") }
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
                onOpenConversation = { conversationId -> navController.navigate("chat/$conversationId") }
            )
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
