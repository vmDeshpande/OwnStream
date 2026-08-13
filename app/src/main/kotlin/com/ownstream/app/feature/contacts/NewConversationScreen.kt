package com.ownstream.app.feature.contacts

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewConversationScreen(
    onBack: () -> Unit,
    onConversationCreated: (String) -> Unit,
    viewModel: NewConversationViewModel = hiltViewModel()
) {
    var remoteId by remember { mutableStateOf("") }
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New Conversation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
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
                "Enter the OwnStream ID of the person you want to message.",
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.height(24.dp))
            OutlinedTextField(
                value = remoteId,
                onValueChange = { remoteId = it },
                label = { Text("OwnStream ID (os_...)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("os_xxxxxxxx") }
            )
            Spacer(modifier = Modifier.height(32.dp))
            
            if (uiState is NewConversationUiState.Error) {
                Text(
                    text = (uiState as NewConversationUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(modifier = Modifier.height(16.dp))
            }

            Button(
                onClick = { viewModel.startChat(remoteId, onConversationCreated) },
                modifier = Modifier.fillMaxWidth(),
                enabled = remoteId.isNotBlank() && uiState !is NewConversationUiState.Loading
            ) {
                if (uiState is NewConversationUiState.Loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Connect")
                }
            }
        }
    }
}
