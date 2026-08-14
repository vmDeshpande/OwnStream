package com.ownstream.app.feature.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ownstream.app.core.crypto.CryptoProvider
import com.ownstream.app.core.network.MessageTransport
import com.ownstream.app.domain.model.*
import com.ownstream.app.domain.repository.ChatRepository
import com.ownstream.app.domain.repository.IdentityRepository
import com.ownstream.app.domain.usecase.GetMessagesUseCase
import com.ownstream.app.domain.usecase.SendMessageUseCase
import com.ownstream.app.domain.usecase.SendMediaUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import com.ownstream.protocol.*
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val getMessagesUseCase: GetMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val sendMediaUseCase: SendMediaUseCase,
    private val chatRepository: ChatRepository,
    private val identityRepository: IdentityRepository,
    private val transport: MessageTransport
) : ViewModel() {

    private val TAG = "ChatViewModel"

    val connectionStatus = transport.observeConnectionStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.ownstream.app.core.network.ConnectionStatus.DISCONNECTED)

    val localIdentity = identityRepository.observeLocalIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _messagesState = MutableStateFlow<List<UiMessage>>(emptyList())
    val messagesState: StateFlow<List<UiMessage>> = _messagesState.asStateFlow()

    private var messagesJob: kotlinx.coroutines.Job? = null

    fun loadMessages(conversationId: String) {
        messagesJob?.cancel()
        messagesJob = viewModelScope.launch {
            getMessagesUseCase(conversationId).collect { list ->
                val uiMessages = list.map { message ->
                    // UI now only reads the already-decrypted content from the DB
                    val (content, type) = when (val p = message.payload) {
                        is MessagePayload.Text -> p.content to "TEXT"
                        is MessagePayload.Media -> p.metadata.fileName to "MEDIA"
                        is MessagePayload.Encrypted -> "[Encrypted Packet]" to "ENCRYPTED"
                    }
                    UiMessage(message, content, type)
                }
                _messagesState.value = uiMessages
            }
        }
    }

    suspend fun getConversation(conversationId: String) = chatRepository.getConversation(conversationId)

    fun sendMessage(conversationId: String, text: String) {
        if (text.isBlank()) return
        val senderId = localIdentity.value?.id ?: "unknown"
        viewModelScope.launch {
            try {
                sendMessageUseCase(conversationId, text, senderId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send message", e)
            }
        }
    }

    fun sendMedia(conversationId: String, fileName: String, mimeType: String, data: ByteArray) {
        val senderId = localIdentity.value?.id ?: "unknown"
        viewModelScope.launch {
            try {
                sendMediaUseCase(conversationId, fileName, mimeType, data, senderId)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send media", e)
            }
        }
    }

    suspend fun downloadMedia(metadata: MediaMetadata): ByteArray? {
        return try {
            val response = transport.downloadMedia(metadata.fileId)
            val encryptedData = ProtocolSerialization.fromBase64(response.encryptedDataBase64)
            val decryptedData = com.ownstream.app.core.crypto.MediaEncryptionManager().decrypt(
                encryptedData, 
                metadata.aesKeyBase64, 
                metadata.aesIvBase64
            )
            decryptedData
        } catch (e: Exception) {
            Log.e(TAG, "Failed to download/decrypt media ${metadata.fileId}", e)
            null
        }
    }
}

data class UiMessage(
    val originalMessage: Message,
    val content: String,
    val type: String = "TEXT"
)
