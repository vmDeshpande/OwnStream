package com.ownstream.app.feature.contacts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ownstream.app.core.crypto.CryptoProvider
import com.ownstream.app.core.network.MessageTransport
import com.ownstream.app.domain.model.*
import com.ownstream.app.domain.repository.ChatRepository
import com.ownstream.app.domain.repository.IdentityRepository
import com.ownstream.protocol.ProtocolValidation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class NewConversationViewModel @Inject constructor(
    private val chatRepository: ChatRepository,
    private val identityRepository: IdentityRepository,
    private val cryptoProvider: CryptoProvider,
    private val transport: MessageTransport
) : ViewModel() {

    private val _uiState = MutableStateFlow<NewConversationUiState>(NewConversationUiState.Idle)
    val uiState = _uiState.asStateFlow()

    fun startChat(remoteId: String, onComplete: (String) -> Unit) {
        if (!ProtocolValidation.isValidOwnStreamId(remoteId)) {
            _uiState.value = NewConversationUiState.Error("Invalid OwnStream ID format")
            return
        }

        viewModelScope.launch {
            _uiState.value = NewConversationUiState.Loading
            try {
                val localIdentity = identityRepository.getLocalIdentity() ?: throw IllegalStateException("Local identity missing")
                
                if (localIdentity.id == remoteId) {
                    _uiState.value = NewConversationUiState.Error("You cannot chat with yourself yet.")
                    return@launch
                }

                // 1. Fetch remote bundle
                val bundle = transport.fetchPreKeyBundle(remoteId)
                if (bundle == null) {
                    _uiState.value = NewConversationUiState.Error("User not found or offline.")
                    return@launch
                }

                // 2. Establish Signal session
                cryptoProvider.establishSession(remoteId, bundle)

                // 3. Create conversation locally
                val conversationId = "conv_" + UUID.randomUUID().toString().replace("-", "").take(12)
                val conversation = Conversation(
                    id = conversationId,
                    title = remoteId, // Default to ID for now
                    storageConfig = StorageConfiguration(
                        conversationId = conversationId,
                        providerType = StorageProviderType.LOCAL
                    ),
                    participants = listOf(
                        Participant(localIdentity.id, localIdentity.username, "OWNER"),
                        Participant(remoteId, remoteId, "MEMBER")
                    )
                )
                chatRepository.createConversation(conversation)

                _uiState.value = NewConversationUiState.Success(conversationId)
                onComplete(conversationId)
            } catch (e: Exception) {
                _uiState.value = NewConversationUiState.Error(e.message ?: "Failed to start chat")
            }
        }
    }
}

sealed class NewConversationUiState {
    object Idle : NewConversationUiState()
    object Loading : NewConversationUiState()
    data class Success(val conversationId: String) : NewConversationUiState()
    data class Error(val message: String) : NewConversationUiState()
}
