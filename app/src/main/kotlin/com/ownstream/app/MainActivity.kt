package com.ownstream.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.rememberNavController
import com.ownstream.app.core.messaging.MessageReceiver
import com.ownstream.app.feature.onboarding.OnboardingViewModel
import com.ownstream.app.ui.navigation.OwnStreamNavigation
import com.ownstream.app.ui.navigation.Screen
import com.ownstream.app.ui.theme.OwnStreamTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private val onboardingViewModel: OnboardingViewModel by viewModels()
    
    @Inject
    lateinit var messageReceiver: MessageReceiver

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Ensure connection starts when identity is available
        // messageReceiver is also started here
        messageReceiver.startObserving()
        
        setContent {
            OwnStreamTheme {
                val navController = rememberNavController()
                val identity by onboardingViewModel.localIdentity.collectAsState()

                // Determine start destination based on whether identity exists
                val startDestination = if (identity == null) {
                    Screen.OnboardingWelcome.route
                } else {
                    Screen.Main.route
                }

                OwnStreamNavigation(
                    navController = navController,
                    startDestination = startDestination
                )
            }
        }
    }
}
