package com.ownstream.app.ui.navigation

sealed class Screen(val route: String) {
    object OnboardingWelcome : Screen("onboarding_welcome")
    object OnboardingIdentity : Screen("onboarding_identity")
    object OnboardingStorage : Screen("onboarding_storage")
    
    object Main : Screen("main")
    object Chats : Screen("chats")
    object Contacts : Screen("contacts")
    object Settings : Screen("settings")
    
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
}
