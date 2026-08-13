package com.ownstream.app.feature.chat

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ownstream.app.core.crypto.CryptoProvider
import com.ownstream.app.core.network.MessageTransport
import com.ownstream.app.core.network.NetworkMessageTransport
import com.ownstream.app.domain.model.Message
import com.ownstream.app.domain.model.MessagePayload
import com.ownstream.app.domain.repository.ChatRepository
import com.ownstream.app.domain.repository.IdentityRepository
import com.ownstream.app.domain.usecase.GetMessagesUseCase
import com.ownstream.app.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val getMessagesUseCase: GetMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val chatRepository: ChatRepository,
    private val identityRepository: IdentityRepository,
    private val cryptoProvider: CryptoProvider,
    private val transport: MessageTransport
) : ViewModel() {

    private val TAG = "ChatViewModel"

    val connectionStatus = transport.observeConnectionStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.ownstream.app.core.network.ConnectionStatus.DISCONNECTED)

    fun messages(conversationId: String): StateFlow<List<UiMessage>> {
        val transport = transport
        if (transport is NetworkMessageTransport) {
            localIdentity.value?.id?.let { id ->
                viewModelScope.launch {
                    transport.connect(id)
                }
            }
        }

        return getMessagesUseCase(conversationId)
            .map { list ->
                val myId = localIdentity.value?.id
                // Use a proper suspend-aware mapping
                list.map { message ->
                    val decryptedContent = when (val p = message.payload) {
                        is MessagePayload.Text -> p.content
                        is MessagePayload.Encrypted -> {
                            if (message.senderId == myId) {
                                "[Encrypted Outgoing]"
                            } else {
                                try {
                                    // NO runBlocking here. Flow.map is already suspend.
                                    cryptoProvider.decryptPayload(p.encryptedPayload, message.senderId)
                                } catch (e: Exception) {
                                    Log.e(TAG, "Decryption error for ${message.id}: ${e.message}")
                                    "[Decryption Failed]"
                                }
                            }
                        }
                    }
                    UiMessage(message, decryptedContent)
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    suspend fun getConversation(conversationId: String) = chatRepository.getConversation(conversationId)

    val localIdentity = identityRepository.observeLocalIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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
}

data class UiMessage(
    val originalMessage: Message,
    val content: String
)
