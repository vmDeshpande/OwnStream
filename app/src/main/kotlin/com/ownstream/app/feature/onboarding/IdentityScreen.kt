package com.ownstream.app.feature.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Your Identity") })
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (identity == null) {
                Text(
                    "Choose a username. Your identity is generated locally and never leaves your device.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
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
                    onClick = { if (username.isNotBlank()) viewModel.createIdentity(username, {}) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = username.isNotBlank()
                ) {
                    Text("Generate Identity")
                }
            } else {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    "Identity Generated!",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Your unique OwnStream ID:",
                    style = MaterialTheme.typography.bodyMedium
                )
                SelectionContainer {
                    Text(
                        text = identity!!.id,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(8.dp)
                    )
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(
                        onClick = { 
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(identity!!.id))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Copy ID")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Button(
                        onClick = onIdentityCreated,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Continue")
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                Text(
                    "Share this ID with others so they can message you securely.",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}
