package com.ownstream.app.feature.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ownstream.app.domain.model.Conversation
import com.ownstream.app.domain.model.Message
import com.ownstream.app.domain.model.MessagePayload
import com.ownstream.app.ui.common.StorageBadge

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messages(conversationId).collectAsState()
    val localIdentity by viewModel.localIdentity.collectAsState()
    var conversation by remember { mutableStateOf<Conversation?>(null) }
    var text by remember { mutableStateOf("") }

    LaunchedEffect(conversationId) {
        conversation = viewModel.getConversation(conversationId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(conversation?.title ?: "Chat", style = MaterialTheme.typography.titleMedium)
                        conversation?.let {
                            StorageBadge(providerType = it.storageConfig.providerType)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                Row(
                    modifier = Modifier
                        .padding(8.dp)
                        .fillMaxWidth()
                        .imePadding(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message...") },
                        maxLines = 4
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (text.isNotBlank()) {
                                viewModel.sendMessage(conversationId, text)
                                text = ""
                            }
                        },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        )
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            reverseLayout = false,
            contentPadding = PaddingValues(16.dp)
        ) {
            items(messages) { uiMessage ->
                val isFromMe = uiMessage.originalMessage.senderId == localIdentity?.id
                MessageBubble(uiMessage, isFromMe)
                Spacer(modifier = Modifier.height(8.dp))
            }
        }
    }
}

@Composable
fun MessageBubble(uiMessage: UiMessage, isFromMe: Boolean) {
    val message = uiMessage.originalMessage
    val alignment = if (isFromMe) Alignment.End else Alignment.Start
    val color = if (isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer
    val contentColor = if (isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer
    val shape = if (isFromMe) {
        RoundedCornerShape(16.dp, 16.dp, 0.dp, 16.dp)
    } else {
        RoundedCornerShape(16.dp, 16.dp, 16.dp, 0.dp)
    }

    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Box(
            modifier = Modifier
                .clip(shape)
                .background(color)
                .padding(12.dp)
        ) {
            Text(text = uiMessage.content, color = contentColor)
        }
        Text(
            text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(message.timestamp),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
        )
    }
}
