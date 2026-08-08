package com.ownstream.app.domain.usecase

import com.ownstream.app.core.crypto.CryptoProvider
import com.ownstream.app.core.network.MessageTransport
import com.ownstream.app.domain.model.Message
import com.ownstream.app.domain.model.MessagePayload
import com.ownstream.app.domain.repository.ChatRepository
import java.util.UUID
import javax.inject.Inject

class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val cryptoProvider: CryptoProvider,
    private val transport: MessageTransport
) {
    suspend operator fun invoke(conversationId: String, text: String, senderId: String) {
        val conversation = chatRepository.getConversation(conversationId) ?: return
        val recipientIds = conversation.participants.map { it.identityId }
        
        // 1. Pass through CryptoProvider boundary
        val encryptedPayload = cryptoProvider.encryptPayload(text, recipientIds)
        
        val message = Message(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = senderId,
            payload = MessagePayload.Encrypted(encryptedPayload),
            timestamp = System.currentTimeMillis()
        )

        // 2. Save to storage
        chatRepository.sendMessage(message)

        // 3. Send via transport
        transport.send(message)
    }
}
