package com.ownstream.app.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.ownstream.app.domain.model.Identity
import com.ownstream.protocol.EncryptedPayload
import com.ownstream.protocol.ProtocolPreKeyBundle
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.UUID
import javax.inject.Inject

class LocalCryptoProvider @Inject constructor() : CryptoProvider {

    private val KEY_ALIAS = "ownstream_identity_key"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"

    override suspend fun generateIdentity(username: String): Identity {
        val keyPairGenerator = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_EC,
            ANDROID_KEYSTORE
        )

        val parameterSpec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
        ).run {
            setDigests(KeyProperties.DIGEST_SHA256, KeyProperties.DIGEST_SHA512)
            build()
        }

        keyPairGenerator.initialize(parameterSpec)
        val keyPair = keyPairGenerator.generateKeyPair()

        val publicKeyBytes = keyPair.public.encoded
        val publicKeyString = Base64.encodeToString(publicKeyBytes, Base64.NO_WRAP)
        
        // Generate a random ID for now, prefixed with os_
        val id = "os_" + UUID.randomUUID().toString().take(8)

        return Identity(
            id = id,
            username = username,
            publicKey = publicKeyString,
            isLocal = true
        )
    }

    override suspend fun encryptPayload(payload: String, recipientIds: List<String>): EncryptedPayload {
        // Placeholder: In a real implementation, we would use the recipient's public keys.
        // For MVP, we return the payload as-is but marked as unencrypted.
        return EncryptedPayload(
            dataBase64 = com.ownstream.protocol.ProtocolSerialization.toBase64(payload.toByteArray()),
            algorithm = "NONE",
            isEncrypted = false,
            metadata = mapOf("note" to "E2EE not yet integrated")
        )
    }

    override suspend fun decryptPayload(encryptedPayload: EncryptedPayload, senderId: String): String {
        return String(com.ownstream.protocol.ProtocolSerialization.fromBase64(encryptedPayload.dataBase64))
    }

    override suspend fun hasSession(recipientId: String): Boolean = true

    override suspend fun establishSession(recipientId: String, bundle: ProtocolPreKeyBundle) {
        // No-op for MVP
    }

    override suspend fun getLocalPreKeyBundle(): ProtocolPreKeyBundle {
        throw UnsupportedOperationException("LocalCryptoProvider does not support PreKey bundles")
    }

    override suspend fun getIdentityFingerprint(identityId: String): String {
        // Placeholder: Generate a fingerprint from the public key.
        return "Fingerprint for $identityId"
    }
}
