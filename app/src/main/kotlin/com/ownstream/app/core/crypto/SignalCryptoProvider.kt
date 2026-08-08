package com.ownstream.app.core.crypto

import android.util.Base64
import android.util.Log
import com.ownstream.app.domain.model.Identity
import com.ownstream.app.domain.repository.IdentityRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.*
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.*
import org.signal.libsignal.protocol.util.KeyHelper
import java.util.*
import javax.inject.Inject

class SignalCryptoProvider @Inject constructor(
    private val store: SignalProtocolStoreAdapter,
    private val identityRepository: IdentityRepository
) : CryptoProvider {

    private val TAG = "SignalCryptoProvider"

    override suspend fun generateIdentity(username: String): Identity = withContext(Dispatchers.Default) {
        val identityKeyPair = IdentityKeyPair.generate()
        val registrationId = KeyHelper.generateRegistrationId(false)
        
        store.saveLocalIdentity(registrationId, identityKeyPair)

        // Map to OwnStream domain model
        val id = "os_" + UUID.randomUUID().toString().take(8)

        Identity(
            id = id,
            username = username,
            publicKey = Base64.encodeToString(identityKeyPair.publicKey.publicKey.serialize(), Base64.NO_WRAP),
            isLocal = true
        )
    }

    override suspend fun encryptPayload(payload: String, recipientIds: List<String>): EncryptedPayload = withContext(Dispatchers.Default) {
        val localIdentity = identityRepository.getLocalIdentity() ?: throw IllegalStateException("Local identity missing")
        
        // Filter out self from recipients for 1:1 E2EE
        val actualRecipients = recipientIds.filter { it != localIdentity.id }
        
        if (actualRecipients.size > 1) {
            throw UnsupportedOperationException("Multi-recipient encryption not supported in Step 4")
        }
        if (actualRecipients.isEmpty()) {
            throw IllegalArgumentException("No recipients specified for encryption (excluding self)")
        }

        val recipientId = actualRecipients.first()
        val remoteAddress = SignalProtocolAddress(recipientId, 1)
        val localAddress = SignalProtocolAddress(localIdentity.id, 1)

        if (!store.containsSession(remoteAddress)) {
            throw IllegalStateException("No secure session established for $recipientId")
        }

        val cipher = SessionCipher(store, localAddress, remoteAddress)
        val ciphertext = cipher.encrypt(payload.toByteArray())

        EncryptedPayload(
            data = ciphertext.serialize(),
            algorithm = "SIGNAL_V1",
            isEncrypted = true,
            metadata = mapOf(
                "type" to ciphertext.type.toString(),
                "senderId" to localIdentity.id
            )
        )
    }

    override suspend fun decryptPayload(encryptedPayload: EncryptedPayload, senderId: String): String = withContext(Dispatchers.Default) {
        if (encryptedPayload.algorithm == "NONE") {
            return@withContext String(encryptedPayload.data)
        }

        if (encryptedPayload.algorithm != "SIGNAL_V1") {
            return@withContext "Unsupported algorithm: ${encryptedPayload.algorithm}"
        }

        val localIdentity = identityRepository.getLocalIdentity() ?: throw IllegalStateException("Local identity missing")
        val localAddress = SignalProtocolAddress(localIdentity.id, 1)
        val remoteAddress = SignalProtocolAddress(senderId, 1)

        val cipher = SessionCipher(store, localAddress, remoteAddress)
        
        val messageType = encryptedPayload.metadata["type"]?.toInt() ?: throw IllegalArgumentException("Missing message type metadata")
        
        val decryptedBytes = when (messageType) {
            CiphertextMessage.PREKEY_TYPE -> {
                val message = PreKeySignalMessage(encryptedPayload.data)
                cipher.decrypt(message)
            }
            CiphertextMessage.WHISPER_TYPE -> {
                val message = SignalMessage(encryptedPayload.data)
                cipher.decrypt(message)
            }
            else -> throw IllegalArgumentException("Unknown Signal message type: $messageType")
        }

        String(decryptedBytes)
    }

    override suspend fun hasSession(recipientId: String): Boolean = withContext(Dispatchers.IO) {
        val address = SignalProtocolAddress(recipientId, 1)
        store.containsSession(address)
    }

    override suspend fun establishSession(recipientId: String, bundle: ProtocolPreKeyBundle) = withContext(Dispatchers.Default) {
        val localIdentity = identityRepository.getLocalIdentity() ?: throw IllegalStateException("Local identity missing")
        val localAddress = SignalProtocolAddress(localIdentity.id, 1)
        val remoteAddress = SignalProtocolAddress(recipientId, 1)

        val preKeyBundle = PreKeyBundle(
            bundle.registrationId,
            bundle.deviceId,
            bundle.preKeyId,
            bundle.preKeyPublic?.let { ECPublicKey(it) },
            bundle.signedPreKeyId,
            ECPublicKey(bundle.signedPreKeyPublic),
            bundle.signedPreKeySignature,
            IdentityKey(ECPublicKey(bundle.identityKey)),
            bundle.kyberPreKeyId,
            KEMPublicKey(bundle.kyberPreKeyPublic),
            bundle.kyberPreKeySignature
        )

        val builder = SessionBuilder(store, remoteAddress, localAddress)
        builder.process(preKeyBundle)
        Log.d(TAG, "Established Signal session with $recipientId")
        Unit
    }

    override suspend fun getLocalPreKeyBundle(): ProtocolPreKeyBundle = withContext(Dispatchers.Default) {
        val identityKeyPair = store.identityKeyPair
        val registrationId = store.localRegistrationId
        
        // In a real app, we would rotate these. For now, generate on demand or fetch existing.
        // We'll generate a single set for the bundle.
        val preKeyId = 1
        val preKey = ECKeyPair.generate()
        store.storePreKey(preKeyId, PreKeyRecord(preKeyId, preKey))

        val signedPreKeyId = 1
        val signedPreKey = ECKeyPair.generate()
        val signature = identityKeyPair.privateKey.calculateSignature(signedPreKey.publicKey.serialize())
        store.storeSignedPreKey(signedPreKeyId, SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), signedPreKey, signature))

        val kyberPreKeyId = 1
        val kyberPreKey = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyberSignature = identityKeyPair.privateKey.calculateSignature(kyberPreKey.publicKey.serialize())
        store.storeKyberPreKey(kyberPreKeyId, KyberPreKeyRecord(kyberPreKeyId, System.currentTimeMillis(), kyberPreKey, kyberSignature))

        ProtocolPreKeyBundle(
            registrationId = registrationId,
            deviceId = 1,
            preKeyId = preKeyId,
            preKeyPublic = preKey.publicKey.serialize(),
            signedPreKeyId = signedPreKeyId,
            signedPreKeyPublic = signedPreKey.publicKey.serialize(),
            signedPreKeySignature = signature,
            identityKey = identityKeyPair.publicKey.publicKey.serialize(),
            kyberPreKeyId = kyberPreKeyId,
            kyberPreKeyPublic = kyberPreKey.publicKey.serialize(),
            kyberPreKeySignature = kyberSignature
        )
    }

    override suspend fun getIdentityFingerprint(identityId: String): String {
        return "Fingerprint for $identityId"
    }
}
