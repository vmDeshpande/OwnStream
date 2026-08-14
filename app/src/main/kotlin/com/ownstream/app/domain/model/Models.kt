package com.ownstream.app.domain.model

import com.ownstream.protocol.EncryptedPayload
import kotlinx.serialization.Serializable

@Serializable
data class Identity(
    val id: String,
    val username: String,
    val publicKey: String,
    val createdAt: Long = System.currentTimeMillis(),
    val isLocal: Boolean = false
)

@Serializable
data class Conversation(
    val id: String,
    val title: String?,
    val storageConfig: StorageConfiguration,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val participants: List<Participant> = emptyList()
)

@Serializable
data class Participant(
    val identityId: String,
    val displayName: String?,
    val role: String = "MEMBER"
)

@Serializable
data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val payload: MessagePayload,
    val timestamp: Long = System.currentTimeMillis(),
    val status: MessageStatus = MessageStatus.SENDING,
    val sequenceNumber: Long = 0
)

@Serializable
sealed class MessagePayload {
    @Serializable
    data class Text(val content: String) : MessagePayload()
    @Serializable
    data class Encrypted(val encryptedPayload: EncryptedPayload) : MessagePayload()
    @Serializable
    data class Media(val metadata: MediaMetadata) : MessagePayload()
}

@Serializable
data class MediaMetadata(
    val fileId: String,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val aesKeyBase64: String,
    val aesIvBase64: String,
    val thumbnailBase64: String? = null
)


@Serializable
enum class MessageStatus {
    SENDING, SENT, DELIVERED, READ, FAILED, RECEIVED
}

@Serializable
data class StorageConfiguration(
    val conversationId: String,
    val providerType: StorageProviderType,
    val connectionDetails: Map<String, String> = emptyMap()
)

@Serializable
enum class StorageProviderType {
    LOCAL, SELF_HOSTED, CLOUD, ADVANCED
}
