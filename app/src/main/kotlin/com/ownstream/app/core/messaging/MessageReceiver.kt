package com.ownstream.app.core.messaging

import android.util.Log
import com.ownstream.app.core.network.MessageTransport
import com.ownstream.app.domain.model.*
import com.ownstream.app.domain.repository.ChatRepository
import com.ownstream.app.domain.repository.IdentityRepository
import com.ownstream.protocol.MessageEnvelope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageReceiver @Inject constructor(
    private val transport: MessageTransport,
    private val chatRepository: ChatRepository,
    private val identityRepository: IdentityRepository
) {
    private val TAG = "MessageReceiver"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startObserving() {
        Log.i(TAG, "Starting to observe incoming messages")
        transport.observeIncomingMessages()
            .onEach { envelope ->
                handleEnvelope(envelope)
            }
            .launchIn(scope)
    }

    private suspend fun handleEnvelope(envelope: MessageEnvelope) {
        Log.d(TAG, "Received envelope: ${envelope.messageId} for conversation: ${envelope.conversationId}")
        
        // 1. Ensure conversation exists to satisfy Foreign Key constraint
        val existingConversation = chatRepository.getConversation(envelope.conversationId)
        if (existingConversation == null) {
            Log.i(TAG, "Creating missing conversation: ${envelope.conversationId}")
            val localIdentity = identityRepository.getLocalIdentity() ?: return
            
            val newConversation = Conversation(
                id = envelope.conversationId,
                title = envelope.senderId, // Default to sender ID
                storageConfig = StorageConfiguration(
                    conversationId = envelope.conversationId,
                    providerType = StorageProviderType.LOCAL
                ),
                participants = listOf(
                    Participant(localIdentity.id, localIdentity.username, "OWNER"),
                    Participant(envelope.senderId, envelope.senderId, "MEMBER")
                )
            )
            chatRepository.createConversation(newConversation)
        }

        // 2. Map envelope to domain Message
        val message = Message(
            id = envelope.messageId,
            conversationId = envelope.conversationId,
            senderId = envelope.senderId,
            payload = MessagePayload.Encrypted(envelope.encryptedPayload),
            timestamp = envelope.timestamp,
            status = MessageStatus.RECEIVED
        )

        // 3. Save to repository (Room)
        try {
            chatRepository.sendMessage(message)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save received message", e)
        }
    }
}
