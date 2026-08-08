package com.ownstream.app.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IdentityScreen(
    onIdentityCreated: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    var username by remember { mutableStateOf("") }
    val identity by viewModel.localIdentity.collectAsState()

    LaunchedEffect(identity) {
        if (identity != null) {
            // If identity already exists, skip (though we shouldn't be here)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Create Identity") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Choose a username. This will be your identity on OwnStream.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(32.dp))
            Button(
                onClick = { if (username.isNotBlank()) viewModel.createIdentity(username, onIdentityCreated) },
                modifier = Modifier.fillMaxWidth(),
                enabled = username.isNotBlank()
            ) {
                Text("Continue")
            }
        }
    }
}
