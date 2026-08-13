package com.ownstream.app.feature.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ownstream.app.core.network.ConnectionStatus
import com.ownstream.app.domain.model.Conversation
import com.ownstream.app.ui.common.StorageBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatSelected: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onNewMessageClick: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val conversations by viewModel.conversations.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val localIdentity by viewModel.localIdentity.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("OwnStream")
                        ConnectionIndicator(connectionStatus)
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onNewMessageClick) {
                Icon(Icons.Default.Add, contentDescription = "New Message")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            localIdentity?.let { identity ->
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    identity.username.take(1).uppercase(),
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(identity.username, style = MaterialTheme.typography.labelLarge)
                            SelectionContainer {
                                Text(
                                    identity.id,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }
                }
            }

            if (conversations.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No conversations yet.", style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onNewMessageClick) {
                            Text("Start a conversation")
                        }
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(conversations) { conversation ->
                        ConversationItem(conversation) { onChatSelected(conversation.id) }
                    }
                }
            }
        }
    }
}

@Composable
fun ConnectionIndicator(status: ConnectionStatus) {
    val (text, color) = when (status) {
        ConnectionStatus.DISCONNECTED -> "Offline" to MaterialTheme.colorScheme.error
        ConnectionStatus.CONNECTING -> "Connecting..." to Color(0xFFFFA500) // Orange
        ConnectionStatus.CONNECTED -> "Connected" to Color(0xFF4CAF50) // Green
        ConnectionStatus.ERROR -> "Connection Error" to MaterialTheme.colorScheme.error
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color
    )
}

@Composable
fun ConversationItem(conversation: Conversation, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onClick() },
        headlineContent = { 
            Text(conversation.title ?: "Untitled Chat", fontWeight = FontWeight.Bold)
        },
        supportingContent = {
            StorageBadge(providerType = conversation.storageConfig.providerType)
        },
        leadingContent = {
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = (conversation.title ?: "?").take(1).uppercase(),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    )
}
