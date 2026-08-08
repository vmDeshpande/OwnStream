package com.ownstream.app.feature.conversations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ownstream.app.domain.model.Conversation
import com.ownstream.app.ui.common.StorageBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatSelected: (String) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: ChatListViewModel = hiltViewModel()
) {
    val conversations by viewModel.conversations.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OwnStream") },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.createDemoConversation() }) {
                Icon(Icons.Default.Add, contentDescription = "New Demo Chat")
            }
        }
    ) { padding ->
        if (conversations.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No conversations yet.", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { viewModel.createDemoConversation() }) {
                        Text("Create Local Demo Chat")
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                items(conversations) { conversation ->
                    ConversationItem(conversation) { onChatSelected(conversation.id) }
                }
            }
        }
    }
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
