package com.ownstream.app.feature.conversations

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ownstream.app.core.network.ConnectionStatus
import com.ownstream.app.domain.model.Conversation
import com.ownstream.app.ui.common.StorageBadge
import com.ownstream.app.ui.theme.IdentityColorProvider

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
            LargeTopAppBar(
                title = { 
                    Column {
                        Text("OwnStream", fontWeight = FontWeight.Black)
                        ConnectionIndicator(connectionStatus)
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.largeTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewMessageClick,
                icon = { Icon(Icons.Default.Add, "New Chat") },
                text = { Text("New Message") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Self Profile Section
            item {
                localIdentity?.let { identity ->
                    ProfileHeader(identity.username, identity.id)
                }
            }

            item {
                Text(
                    "Conversations",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 8.dp)
                )
            }

            if (conversations.isEmpty()) {
                item {
                    EmptyChatsState(onNewMessageClick)
                }
            } else {
                items(conversations) { conversation ->
                    ConversationItem(conversation) { onChatSelected(conversation.id) }
                }
            }
        }
    }
}

@Composable
fun ProfileHeader(username: String, id: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f))
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(username, id, size = 64.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(username, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                SelectionContainer {
                    Text(
                        id, 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.primary,
                        letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

@Composable
fun Avatar(name: String, id: String, size: androidx.compose.ui.unit.Dp = 48.dp) {
    val color = IdentityColorProvider.getColorForId(id)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    listOf(color, color.copy(alpha = 0.7f))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = name.take(1).uppercase(),
            style = if (size > 50.dp) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun ConnectionIndicator(status: ConnectionStatus) {
    val (text, color) = when (status) {
        ConnectionStatus.DISCONNECTED -> "Relay Offline" to MaterialTheme.colorScheme.error
        ConnectionStatus.CONNECTING -> "Connecting..." to Color(0xFFFFA500)
        ConnectionStatus.CONNECTED -> "Secure Connection Active" to Color(0xFF4CAF50)
        ConnectionStatus.ERROR -> "Relay Connection Error" to MaterialTheme.colorScheme.error
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = color.copy(alpha = 0.8f))
    }
}

@Composable
fun ConversationItem(conversation: Conversation, onClick: () -> Unit) {
    // Priority: Display Name -> OS ID
    val participant = conversation.participants.firstOrNull { it.displayName != null && it.displayName != it.identityId }
    val title = participant?.displayName ?: conversation.title ?: "Anonymous"
    val subtitle = if (participant != null) conversation.title else "End-to-End Encrypted"

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = Color.Transparent
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Avatar(title, conversation.id)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title, 
                        style = MaterialTheme.typography.titleMedium, 
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Default.Shield, "", modifier = Modifier.size(14.dp), tint = Color(0xFF4CAF50).copy(alpha = 0.6f))
                }
                Text(
                    subtitle ?: "", 
                    style = MaterialTheme.typography.bodySmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            StorageBadge(providerType = conversation.storageConfig.providerType)
        }
    }
}

@Composable
fun EmptyChatsState(onNewMessageClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(120.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Shield, null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Your inbox is empty", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "Messages are encrypted on your device and sent through your chosen relay.",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = onNewMessageClick, shape = RoundedCornerShape(12.dp)) {
            Text("Start a Secure Chat")
        }
    }
}
