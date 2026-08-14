package com.ownstream.app.domain.usecase

import com.ownstream.app.core.crypto.CryptoProvider
import com.ownstream.app.core.crypto.MediaEncryptionManager
import com.ownstream.app.core.network.MessageTransport
import com.ownstream.app.domain.model.*
import com.ownstream.app.domain.repository.ChatRepository
import com.ownstream.protocol.MessageEnvelope
import com.ownstream.protocol.ProtocolSerialization
import com.ownstream.protocol.UploadMediaRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject

class SendMediaUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
    private val cryptoProvider: CryptoProvider,
    private val mediaEncryptionManager: MediaEncryptionManager,
    private val transport: MessageTransport
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend operator fun invoke(
        conversationId: String, 
        fileName: String, 
        mimeType: String, 
        data: ByteArray, 
        senderId: String,
        thumbnail: ByteArray? = null
    ) {
        val conversation = chatRepository.getConversation(conversationId) ?: return
        val recipients = conversation.participants.filter { it.identityId != senderId }
        val recipientIds = recipients.map { it.identityId }

        // 1. Encrypt file with AES-GCM
        val encryptedFile = mediaEncryptionManager.encrypt(data)

        // 2. Upload encrypted file to relay
        val uploadRequest = UploadMediaRequest(
            fileName = fileName,
            encryptedDataBase64 = ProtocolSerialization.toBase64(encryptedFile.data)
        )
        val fileId = transport.uploadMedia(uploadRequest)

        // 3. Prepare Media Metadata
        val metadata = MediaMetadata(
            fileId = fileId,
            fileName = fileName,
            mimeType = mimeType,
            size = data.size.toLong(),
            aesKeyBase64 = encryptedFile.keyBase64,
            aesIvBase64 = encryptedFile.ivBase64,
            thumbnailBase64 = thumbnail?.let { ProtocolSerialization.toBase64(it) }
        )

        val payload = MessagePayload.Media(metadata)
        val payloadJson = json.encodeToString(payload)

        // 4. Encrypt Metadata via Signal
        val encryptedPayload = cryptoProvider.encryptPayload(payloadJson, recipientIds)

        val messageId = UUID.randomUUID().toString()
        val timestamp = System.currentTimeMillis()

        // 5. Save locally (as Media payload, so we don't need to re-decrypt)
        val localMessage = Message(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            payload = payload,
            timestamp = timestamp,
            status = MessageStatus.SENDING
        )
        chatRepository.sendMessage(localMessage)

        // 6. Send envelope
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
