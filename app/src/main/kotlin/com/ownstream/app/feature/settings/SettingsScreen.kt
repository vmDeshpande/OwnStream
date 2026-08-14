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
import androidx.compose.ui.text.font.FontWeight
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
                ProfileSection(identity?.username ?: "Anonymous", identity?.id ?: "No ID")
            }
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
            item {
                SettingsItem(
                    "Relay Server", 
                    Icons.Default.Dns, 
                    relayUrl,
                    onClick = { showRelayDialog = true }
                )
            }
            item {
                SettingsItem(
                    "Security", 
                    Icons.Default.Security, 
                    "Signal E2EE enabled. Private keys stored in Android Keystore."
                )
            }
            item {
                SettingsItem(
                    "Storage", 
                    Icons.Default.Storage, 
                    "Current Mode: Local Device Storage"
                )
            }
            item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
            item {
                SettingsItem(
                    "About", 
                    Icons.Default.Info, 
                    "OwnStream MVP v0.1.0"
                )
            }
        }

        if (showRelayDialog) {
            AlertDialog(
                onDismissRequest = { showRelayDialog = false },
                title = { Text("Relay URL") },
                text = {
                    Column {
                        Text("Connect your physical device to the PC's LAN IP for testing.", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedTextField(
                            value = relayUrl,
                            onValueChange = { relayUrl = it },
                            label = { Text("Server Address") },
                            singleLine = true,
                            placeholder = { Text("http://192.168.x.x:8080") }
                        )
                    }
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
            Text(username, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            SelectionContainer {
                Text(id, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
fun SettingsItem(title: String, icon: ImageVector, subtitle: String? = null, onClick: () -> Unit = {}) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
        modifier = Modifier.clickable { onClick() }
    )
}
