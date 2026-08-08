package com.ownstream.app.feature.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ownstream.app.core.crypto.CryptoProvider
import com.ownstream.app.domain.model.Message
import com.ownstream.app.domain.model.MessagePayload
import com.ownstream.app.domain.repository.ChatRepository
import com.ownstream.app.domain.repository.IdentityRepository
import com.ownstream.app.domain.usecase.GetMessagesUseCase
import com.ownstream.app.domain.usecase.SendMessageUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val getMessagesUseCase: GetMessagesUseCase,
    private val sendMessageUseCase: SendMessageUseCase,
    private val chatRepository: ChatRepository,
    private val identityRepository: IdentityRepository,
    private val cryptoProvider: CryptoProvider
) : ViewModel() {

    fun messages(conversationId: String) = getMessagesUseCase(conversationId)
        .map { list ->
            list.map { message ->
                val decryptedContent = when (val p = message.payload) {
                    is MessagePayload.Text -> p.content
                    is MessagePayload.Encrypted -> cryptoProvider.decryptPayload(p.encryptedPayload, message.senderId)
                }
                UiMessage(message, decryptedContent)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    suspend fun getConversation(conversationId: String) = chatRepository.getConversation(conversationId)

    val localIdentity = identityRepository.observeLocalIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun sendMessage(conversationId: String, text: String) {
        if (text.isBlank()) return
        val senderId = localIdentity.value?.id ?: "unknown"
        viewModelScope.launch {
            sendMessageUseCase(conversationId, text, senderId)
        }
    }
}

data class UiMessage(
    val originalMessage: Message,
    val content: String
)
