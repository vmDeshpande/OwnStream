package com.ownstream.app.feature.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.ownstream.app.core.network.ConnectionStatus
import com.ownstream.app.domain.model.*
import com.ownstream.app.feature.conversations.Avatar
import com.ownstream.app.feature.conversations.ConnectionIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    onBack: () -> Unit,
    viewModel: ChatViewModel = hiltViewModel()
) {
    val messages by viewModel.messagesState.collectAsState()
    val localIdentity by viewModel.localIdentity.collectAsState()
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    var conversation by remember { mutableStateOf<Conversation?>(null) }
    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    val mediaLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(it)
            val bytes = inputStream?.readBytes()
            if (bytes != null) {
                val cursor = contentResolver.query(it, null, null, null, null)
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val nameIndex = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        val fileName = c.getString(nameIndex) ?: "file"
                        val mimeType = contentResolver.getType(it) ?: "application/octet-stream"
                        viewModel.sendMedia(conversationId, fileName, mimeType, bytes)
                    }
                }
            }
        }
    }

    LaunchedEffect(conversationId) {
        viewModel.loadMessages(conversationId)
        conversation = viewModel.getConversation(conversationId)
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Avatar(conversation?.title ?: "?", conversation?.id ?: "", size = 40.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                conversation?.title ?: "Secure Chat", 
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            ConnectionIndicator(connectionStatus)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            )
        },
        bottomBar = {
            Surface(
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.imePadding()
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { mediaLauncher.launch("*/*") },
                        enabled = connectionStatus == ConnectionStatus.CONNECTED
                    ) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach File")
                    }
                    
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(24.dp)),
                        placeholder = { Text("Message...") },
                        maxLines = 4,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            unfocusedBorderColor = Color.Transparent,
                            focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                        ),
                        enabled = connectionStatus == ConnectionStatus.CONNECTED
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    val canSend = text.isNotBlank() && connectionStatus == ConnectionStatus.CONNECTED
                    FilledIconButton(
                        onClick = {
                            if (text.isNotBlank()) {
                                viewModel.sendMessage(conversationId, text)
                                text = ""
                            }
                        },
                        enabled = canSend,
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { uiMessage ->
                    val isFromMe = uiMessage.originalMessage.senderId == localIdentity?.id
                    MessageBubble(uiMessage, isFromMe, viewModel)
                }
            }
            
            if (messages.isEmpty() && connectionStatus == ConnectionStatus.CONNECTING) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun MessageBubble(uiMessage: UiMessage, isFromMe: Boolean, viewModel: ChatViewModel) {
    val message = uiMessage.originalMessage
    val alignment = if (isFromMe) Alignment.End else Alignment.Start
    val containerColor = if (isFromMe) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
    val contentColor = if (isFromMe) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
    
    val shape = if (isFromMe) {
        RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)
    } else {
        RoundedCornerShape(20.dp, 20.dp, 20.dp, 4.dp)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        if (uiMessage.type == "MEDIA") {
            MediaBubbleContent(uiMessage.originalMessage, viewModel, shape, containerColor, contentColor)
        } else {
            Surface(
                color = containerColor,
                shape = shape,
                tonalElevation = 2.dp,
                modifier = Modifier.widthIn(max = 300.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(
                        text = uiMessage.content,
                        color = contentColor,
                        style = MaterialTheme.typography.bodyLarge,
                        lineHeight = 22.sp
                    )
                    Text(
                        text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(message.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
            }
        }
    }
}

@Composable
fun MediaBubbleContent(
    message: Message, 
    viewModel: ChatViewModel, 
    shape: RoundedCornerShape,
    containerColor: Color,
    contentColor: Color
) {
    val payload = message.payload as? MessagePayload.Media ?: return
    val metadata = payload.metadata
    
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(metadata.fileId) {
        if (metadata.mimeType.startsWith("image/")) {
            isLoading = true
            val data = viewModel.downloadMedia(metadata)
            if (data != null) {
                bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            }
            isLoading = false
        }
    }

    Surface(
        color = containerColor,
        shape = shape,
        tonalElevation = 2.dp,
        modifier = Modifier.width(260.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            // Image Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(shape)
                    .background(Color.Black.copy(alpha = 0.05f)),
                contentAlignment = Alignment.Center
            ) {
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap!!.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp), strokeWidth = 2.dp)
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(32.dp), tint = contentColor)
                        Text(metadata.fileName, style = MaterialTheme.typography.labelSmall, color = contentColor, modifier = Modifier.padding(8.dp))
                    }
                }
            }

            // Overlay Timestamp (WhatsApp style)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(message.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontSize = 10.sp
                )
            }
        }
    }
}
