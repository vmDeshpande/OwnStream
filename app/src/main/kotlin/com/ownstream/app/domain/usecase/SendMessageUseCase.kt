package com.ownstream.app.domain.usecase

import com.ownstream.app.core.crypto.CryptoProvider
import com.ownstream.app.core.network.MessageTransport
import com.ownstream.app.domain.model.Message
import com.ownstream.app.domain.model.MessagePayload
import com.ownstream.app.domain.repository.ChatRepository
import com.ownstream.protocol.MessageEnvelope
import java.util.UUID
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val cryptoProvider: CryptoProvider,
    private val transport: MessageTransport
) {
    suspend operator fun invoke(conversationId: String, text: String, senderId: String) {
        val conversation = chatRepository.getConversation(conversationId) ?: return
        
        // Filter out self from recipients
        val recipients = conversation.participants.filter { it.identityId != senderId }
        val recipientIds = recipients.map { it.identityId }
        
        // 1. Pass through CryptoProvider boundary
        val encryptedPayload = cryptoProvider.encryptPayload(text, recipientIds)
        
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        
        val message = Message(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            payload = MessagePayload.Encrypted(encryptedPayload),
            timestamp = timestamp
        )

        // 2. Save to storage
        chatRepository.sendMessage(message)

        // 3. Send via transport (only for 1:1 in Step 5)
        if (recipients.size == 1) {
            val envelope = MessageEnvelope(
                messageId = messageId,
                conversationId = conversationId,
                senderId = senderId,
                recipientId = recipients.first().identityId,
                timestamp = timestamp,
                encryptedPayload = encryptedPayload
            )
            transport.send(envelope)
        }
    }
}
