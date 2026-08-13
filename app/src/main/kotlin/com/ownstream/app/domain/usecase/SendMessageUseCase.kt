package com.ownstream.app.domain.usecase

import com.ownstream.app.core.crypto.CryptoProvider
import com.ownstream.app.core.network.MessageTransport
import com.ownstream.app.domain.model.Message
import com.ownstream.app.domain.model.MessagePayload
import com.ownstream.app.domain.model.MessageStatus
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
        
        // 1. Encrypt for the network
        val encryptedPayload = cryptoProvider.encryptPayload(text, recipientIds)
        
        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()
        
        // 2. Save locally as TEXT so the sender can always read their own history
        val localMessage = Message(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            payload = MessagePayload.Text(text),
            timestamp = timestamp,
            status = MessageStatus.SENDING
        )

        chatRepository.sendMessage(localMessage)

        // 3. Send via transport
        if (recipients.size == 1) {
            try {
                val envelope = MessageEnvelope(
                    messageId = messageId,
                    conversationId = conversationId,
                    senderId = senderId,
                    recipientId = recipients.first().identityId,
                    timestamp = timestamp,
                    encryptedPayload = encryptedPayload
                )
                transport.send(envelope)
                
                chatRepository.sendMessage(localMessage.copy(status = MessageStatus.SENT))
            } catch (e: Exception) {
                chatRepository.sendMessage(localMessage.copy(status = MessageStatus.FAILED))
            }
        }
    }
}
