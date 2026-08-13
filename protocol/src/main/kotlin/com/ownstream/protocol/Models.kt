package com.ownstream.protocol

import kotlinx.serialization.Serializable

/**
 * Versioned protocol identifier and validation constants.
 */
object OwnStreamProtocol {
    const val VERSION_1 = 1
    
    // Validation constants
    const val MAX_MESSAGE_SIZE_BYTES = 1024 * 1024 // 1 MB
    const val MAX_ENVELOPE_METADATA_SIZE = 4096 // 4 KB
}

/**
 * Core cryptographic payload carrying Signal ciphertext and metadata.
 */
@Serializable
data class EncryptedPayload(
    val dataBase64: String,
    val algorithm: String,
    val isEncrypted: Boolean,
    val metadata: Map<String, String> = emptyMap()
)

/**
 * Public PreKey bundle required to establish a Signal session.
 * Contains only public cryptographic material.
 */
@Serializable
data class ProtocolPreKeyBundle(
    val registrationId: Int,
    val deviceId: Int,
    val preKeyId: Int,
    val preKeyPublicBase64: String?,
    val signedPreKeyId: Int,
    val signedPreKeyPublicBase64: String,
    val signedPreKeySignatureBase64: String,
    val identityKeyBase64: String,
    val kyberPreKeyId: Int,
    val kyberPreKeyPublicBase64: String,
    val kyberPreKeySignatureBase64: String
)

/**
 * Routing envelope for the network transport.
 * Cryptographically blind to the content of the encryptedPayload.
 */
@Serializable
data class MessageEnvelope(
    val messageId: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val timestamp: Long,
    val encryptedPayload: EncryptedPayload
)
