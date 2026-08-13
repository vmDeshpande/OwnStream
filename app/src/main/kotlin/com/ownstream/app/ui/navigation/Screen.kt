package com.ownstream.app.ui.navigation

sealed class Screen(val route: String) {
    object OnboardingWelcome : Screen("onboarding_welcome")
    object OnboardingIdentity : Screen("onboarding_identity")
    
    object Main : Screen("main")
    object Settings : Screen("settings")
    object NewConversation : Screen("new_conversation")
    
    object Chat : Screen("chat/{conversationId}") {
        fun createRoute(conversationId: String) = "chat/$conversationId"
    }
}

