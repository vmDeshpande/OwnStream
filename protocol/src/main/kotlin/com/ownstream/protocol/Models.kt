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
    val data: ByteArray,
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
    val preKeyPublic: ByteArray?,
    val signedPreKeyId: Int,
    val signedPreKeyPublic: ByteArray,
    val signedPreKeySignature: ByteArray,
    val identityKey: ByteArray,
    val kyberPreKeyId: Int,
    val kyberPreKeyPublic: ByteArray,
    val kyberPreKeySignature: ByteArray
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
