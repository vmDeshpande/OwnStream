package com.ownstream.app.core.crypto

import com.ownstream.app.domain.model.Identity
import kotlinx.serialization.Serializable

/**
 * Core abstraction for all cryptographic operations.
 * Isolates the app from specific E2EE protocol implementations.
 */
interface CryptoProvider {
    /**
     * Generates a new identity key pair, protected by Android Keystore.
     */
    suspend fun generateIdentity(username: String): Identity

    /**
     * Encrypts a plaintext payload for the specified recipients.
     * Note: For the MVP, if no protocol is integrated, this may return the plaintext
     * but clearly marked as UNENCRYPTED in metadata.
     */
    suspend fun encryptPayload(payload: String, recipientIds: List<String>): EncryptedPayload

    /**
     * Decrypts an encrypted payload.
     */
    suspend fun decryptPayload(encryptedPayload: EncryptedPayload, senderId: String): String

    /**
     * Checks if a secure session exists for the recipient.
     */
    suspend fun hasSession(recipientId: String): Boolean

    /**
     * Processes a PreKey Bundle to establish a session with a recipient.
     * The bundle should be provided in a serializable format.
     */
    suspend fun establishSession(recipientId: String, bundle: ProtocolPreKeyBundle)

    /**
     * Generates a PreKey Bundle for the local identity to be shared with others.
     */
    suspend fun getLocalPreKeyBundle(): ProtocolPreKeyBundle

    /**
     * Verifies the cryptographic fingerprint of a remote identity.
     */
    suspend fun getIdentityFingerprint(identityId: String): String
}

@Serializable
data class EncryptedPayload(
    val data: ByteArray,
    val algorithm: String,
    val isEncrypted: Boolean,
    val metadata: Map<String, String> = emptyMap()
)

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
