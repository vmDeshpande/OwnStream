package com.ownstream.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.ownstream.app.feature.onboarding.IdentityScreen
import com.ownstream.app.feature.onboarding.StorageSelectionScreen
import com.ownstream.app.feature.onboarding.WelcomeScreen
import com.ownstream.app.feature.conversations.ChatListScreen
import com.ownstream.app.feature.chat.ChatScreen

@Composable
fun OwnStreamNavigation(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.OnboardingWelcome.route) {
            WelcomeScreen(
                onGetStarted = { navController.navigate(Screen.OnboardingIdentity.route) }
            )
        }
        composable(Screen.OnboardingIdentity.route) {
            IdentityScreen(
                onIdentityCreated = { navController.navigate(Screen.OnboardingStorage.route) }
            )
        }
        composable(Screen.OnboardingStorage.route) {
            StorageSelectionScreen(
                onStorageSelected = {
                    navController.navigate(Screen.Main.route) {
                        popUpTo(Screen.OnboardingWelcome.route) { inclusive = true }
                    }
                }
            )
        }
        composable(Screen.Main.route) {
            ChatListScreen(
                onChatSelected = { id -> navController.navigate(Screen.Chat.createRoute(id)) },
                onSettingsClick = { navController.navigate(Screen.Settings.route) }
            )
        }
        composable(Screen.Chat.route) { backStackEntry ->
            val conversationId = backStackEntry.arguments?.getString("conversationId") ?: ""
            ChatScreen(
                conversationId = conversationId,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Screen.Settings.route) {
            com.ownstream.app.feature.settings.SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
