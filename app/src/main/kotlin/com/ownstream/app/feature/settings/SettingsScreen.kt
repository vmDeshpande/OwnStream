package com.ownstream.app.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val identity by viewModel.localIdentity.collectAsState()
    var relayUrl by remember { mutableStateOf(viewModel.getRelayUrl()) }
    var showRelayDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            item {
                ProfileSection(identity?.username ?: "Unknown", identity?.id ?: "No ID")
            }
            item { HorizontalDivider() }
            item {
                SettingsItem(
                    "Relay Server", 
                    Icons.Default.Dns, 
                    relayUrl,
                    onClick = { showRelayDialog = true }
                )
            }
            item {
                SettingsItem("Privacy & Security", Icons.Default.Security, "End-to-end encryption enabled")
            }
            item {
                SettingsItem("Storage", Icons.Default.Storage, "Local Device Storage")
            }
            item { HorizontalDivider() }
            item {
                SettingsItem("About OwnStream", Icons.Default.Info, "Version 0.1.0 (MVP)")
            }
        }

        if (showRelayDialog) {
            AlertDialog(
                onDismissRequest = { showRelayDialog = false },
                title = { Text("Relay URL") },
                text = {
                    OutlinedTextField(
                        value = relayUrl,
                        onValueChange = { relayUrl = it },
                        label = { Text("Server Address") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.updateRelayUrl(relayUrl)
                        showRelayDialog = false
                    }) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRelayDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun ProfileSection(username: String, id: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(64.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.primaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(username.take(1).uppercase(), style = MaterialTheme.typography.headlineLarge)
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column {
            Text(username, style = MaterialTheme.typography.titleLarge)
            SelectionContainer {
                Text(id, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun SettingsItem(title: String, icon: ImageVector, subtitle: String? = null, onClick: () -> Unit = {}) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null) },
        modifier = Modifier.padding(vertical = 4.dp).clickable { onClick() }
    )
}
