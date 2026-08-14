package com.ownstream.app.core.messaging

import android.util.Log
import com.ownstream.app.core.crypto.CryptoProvider
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageReceiver @Inject constructor(
    private val transport: MessageTransport,
    private val chatRepository: ChatRepository,
    private val identityRepository: IdentityRepository,
    private val cryptoProvider: CryptoProvider
) {
    private val TAG = "MessageReceiver"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json { ignoreUnknownKeys = true }

    fun startObserving() {
        Log.i(TAG, "Starting to observe incoming messages")
        transport.observeIncomingMessages()
            .onEach { envelope ->
                handleEnvelope(envelope)
            }
            .launchIn(scope)
    }

    private suspend fun handleEnvelope(envelope: MessageEnvelope) {
        Log.d(TAG, "Processing incoming envelope: ${envelope.messageId}")
        
        try {
            // 1. Decrypt ONCE at the edge
            val decryptedJson = cryptoProvider.decryptPayload(envelope.encryptedPayload, envelope.senderId)
            
            // 2. Parse the decrypted content into a domain payload
            val payload = try {
                json.decodeFromString<MessagePayload>(decryptedJson)
            } catch (e: Exception) {
                // Self-healing: Check if this is a Media JSON without a discriminator
                if (decryptedJson.contains("\"metadata\":") && decryptedJson.contains("\"fileId\":")) {
                    try {
                        val mediaMetadata = json.decodeFromString<MediaMetadata>(
                            // If it's wrapped in {"metadata":...}, extract it
                            if (decryptedJson.startsWith("{\"metadata\":")) {
                                json.parseToJsonElement(decryptedJson).jsonObject["metadata"].toString()
                            } else decryptedJson
                        )
                        MessagePayload.Media(mediaMetadata)
                    } catch (e2: Exception) {
                        MessagePayload.Text(decryptedJson)
                    }
                } else {
                    MessagePayload.Text(decryptedJson)
                }
            }

            // 3. Ensure conversation exists
            ensureConversationExists(envelope)

            // 4. Save the already-decrypted message to the local database
            val message = Message(
                id = envelope.messageId,
                conversationId = envelope.conversationId,
                senderId = envelope.senderId,
                payload = payload, // Saved as Text or Media, NOT Encrypted
                timestamp = envelope.timestamp,
                status = MessageStatus.RECEIVED
            )

            chatRepository.sendMessage(message)
            Log.i(TAG, "Successfully processed and saved message: ${envelope.messageId}")

        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Failed to process incoming envelope", e)
            // Save as error state so user knows something was missed
            saveErrorPlaceholder(envelope)
        }
    }

    private suspend fun ensureConversationExists(envelope: MessageEnvelope) {
        val existing = chatRepository.getConversation(envelope.conversationId)
        if (existing == null) {
            val localIdentity = identityRepository.getLocalIdentity() ?: return
            val newConversation = Conversation(
                id = envelope.conversationId,
                title = envelope.senderId,
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
    }

    private suspend fun saveErrorPlaceholder(envelope: MessageEnvelope) {
        val errorMsg = Message(
            id = envelope.messageId,
            conversationId = envelope.conversationId,
            senderId = envelope.senderId,
            payload = MessagePayload.Text("[Decryption Failed]"),
            timestamp = envelope.timestamp,
            status = MessageStatus.FAILED
        )
        chatRepository.sendMessage(errorMsg)
    }
}
