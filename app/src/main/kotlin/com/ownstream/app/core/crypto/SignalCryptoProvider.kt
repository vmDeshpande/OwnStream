package com.ownstream.app.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64 as AndroidBase64
import android.util.Log
import com.ownstream.app.domain.model.Identity
import com.ownstream.app.domain.repository.IdentityRepository
import com.ownstream.protocol.*
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
import java.security.KeyPairGenerator
import java.util.*
import javax.inject.Inject

class SignalCryptoProvider @Inject constructor(
    private val store: SignalProtocolStoreAdapter,
    private val identityRepository: IdentityRepository,
    @IdentityKeyAlias private val identityKeyAlias: String,
    private val keyStoreProvider: KeyStoreProvider
) : CryptoProvider {

    private val TAG = "SignalCryptoProvider"

    override suspend fun generateIdentity(username: String): Identity = withContext(Dispatchers.Default) {
        Log.d(TAG, "[6E] Generating identity for $username")
        
        // 1. Generate Signal Identity
        val identityKeyPair = IdentityKeyPair.generate()
        val registrationId = KeyHelper.generateRegistrationId(false)
        
        Log.d(TAG, "[6E] Saving Signal identity...")
        store.saveLocalIdentity(registrationId, identityKeyPair)

        // 2. Generate hardware-backed Auth Key for Relay login
        try {
            val keyStore = keyStoreProvider.getKeyStore()
            if (!keyStore.containsAlias(identityKeyAlias)) {
                Log.d(TAG, "[6E] Generating hardware-backed auth key: $identityKeyAlias")
                val kpg = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC, "AndroidKeyStore")
                kpg.initialize(
                    KeyGenParameterSpec.Builder(identityKeyAlias, KeyProperties.PURPOSE_SIGN)
                        .setDigests(KeyProperties.DIGEST_SHA256)
                        .build()
                )
                kpg.generateKeyPair()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to generate Keystore auth key", e)
        }

        // Map to OwnStream domain model
        val id = "os_" + UUID.randomUUID().toString().replace("-", "").take(8)

        Identity(
            id = id,
            username = username,
            publicKey = AndroidBase64.encodeToString(identityKeyPair.publicKey.publicKey.serialize(), AndroidBase64.NO_WRAP),
            isLocal = true
        )
    }

    override suspend fun encryptPayload(payload: String, recipientIds: List<String>): EncryptedPayload = withContext(Dispatchers.Default) {
        val localIdentity = identityRepository.getLocalIdentity() ?: throw IllegalStateException("Local identity missing")
        val actualRecipients = recipientIds.filter { it != localIdentity.id }
        
        if (actualRecipients.size > 1) throw UnsupportedOperationException("Multi-recipient encryption not supported")
        if (actualRecipients.isEmpty()) throw IllegalArgumentException("No recipients specified")

        val recipientId = actualRecipients.first()
        val remoteAddress = SignalProtocolAddress(recipientId, 1)
        val localAddress = SignalProtocolAddress(localIdentity.id, 1)

        if (!store.containsSession(remoteAddress)) {
            throw IllegalStateException("No secure session established for $recipientId")
        }

        val cipher = SessionCipher(store, localAddress, remoteAddress)
        val ciphertext = cipher.encrypt(payload.toByteArray())

        EncryptedPayload(
            dataBase64 = ProtocolSerialization.toBase64(ciphertext.serialize()),
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
            return@withContext String(ProtocolSerialization.fromBase64(encryptedPayload.dataBase64))
        }

        val localIdentity = identityRepository.getLocalIdentity() ?: throw IllegalStateException("Local identity missing")
        val localAddress = SignalProtocolAddress(localIdentity.id, 1)
        val remoteAddress = SignalProtocolAddress(senderId, 1)

        val cipher = SessionCipher(store, localAddress, remoteAddress)
        val messageType = try {
            encryptedPayload.metadata["type"]?.toInt()
        } catch (e: Exception) {
            null
        } ?: throw IllegalArgumentException("Missing or invalid message type in metadata")

        val data = ProtocolSerialization.fromBase64(encryptedPayload.dataBase64)
        
        val decryptedBytes = try {
            when (messageType) {
                CiphertextMessage.PREKEY_TYPE -> cipher.decrypt(PreKeySignalMessage(data))
                CiphertextMessage.WHISPER_TYPE -> cipher.decrypt(SignalMessage(data))
                else -> throw IllegalArgumentException("Unknown Signal type: $messageType")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Signal decryption failed: ${e.message}")
            throw e
        }

        String(decryptedBytes)
    }

    override suspend fun hasSession(recipientId: String): Boolean = withContext(Dispatchers.IO) {
        store.containsSession(SignalProtocolAddress(recipientId, 1))
    }

    override suspend fun establishSession(recipientId: String, bundle: ProtocolPreKeyBundle) = withContext(Dispatchers.Default) {
        val localIdentity = identityRepository.getLocalIdentity() ?: throw IllegalStateException("Local identity missing")
        val localAddress = SignalProtocolAddress(localIdentity.id, 1)
        val remoteAddress = SignalProtocolAddress(recipientId, 1)

        val preKeyBundle = PreKeyBundle(
            bundle.registrationId,
            bundle.deviceId,
            bundle.preKeyId,
            bundle.preKeyPublicBase64?.let { ECPublicKey(ProtocolSerialization.fromBase64(it)) },
            bundle.signedPreKeyId,
            ECPublicKey(ProtocolSerialization.fromBase64(bundle.signedPreKeyPublicBase64)),
            ProtocolSerialization.fromBase64(bundle.signedPreKeySignatureBase64),
            IdentityKey(ECPublicKey(ProtocolSerialization.fromBase64(bundle.identityKeyBase64))),
            bundle.kyberPreKeyId,
            KEMPublicKey(ProtocolSerialization.fromBase64(bundle.kyberPreKeyPublicBase64)),
            ProtocolSerialization.fromBase64(bundle.kyberPreKeySignatureBase64)
        )

        val builder = SessionBuilder(store, remoteAddress, localAddress)
        builder.process(preKeyBundle)
        Log.d(TAG, "Established Signal session with $recipientId")
        Unit
    }

    override suspend fun getLocalPreKeyBundle(): ProtocolPreKeyBundle = withContext(Dispatchers.Default) {
        val identityKeyPair = store.identityKeyPair
        val registrationId = store.localRegistrationId
        
        // 1. Get or generate Signed PreKey
        val signedPreKeyId = 1
        val signedPreKeyRecord = try {
            store.loadSignedPreKey(signedPreKeyId)
        } catch (e: Exception) {
            val key = ECKeyPair.generate()
            val signature = identityKeyPair.privateKey.calculateSignature(key.publicKey.serialize())
            val record = SignedPreKeyRecord(signedPreKeyId, System.currentTimeMillis(), key, signature)
            store.storeSignedPreKey(signedPreKeyId, record)
            record
        }

        // 2. Get or generate Kyber PreKey
        val kyberPreKeyId = 1
        val kyberPreKeyRecord = try {
            store.loadKyberPreKey(kyberPreKeyId)
        } catch (e: Exception) {
            val key = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
            val signature = identityKeyPair.privateKey.calculateSignature(key.publicKey.serialize())
            val record = KyberPreKeyRecord(kyberPreKeyId, System.currentTimeMillis(), key, signature)
            store.storeKyberPreKey(kyberPreKeyId, record)
            record
        }

        // 3. Generate a fresh One-Time PreKey for this bundle
        // In a production app, we would generate a pool of these. For MVP, we'll generate one fresh one
        // and upload it. Since it's a "one-time" key, it's okay to overwrite ID 1 locally after it's used.
        val preKeyId = (System.currentTimeMillis() % 1000000).toInt() + 1
        val preKey = ECKeyPair.generate()
        store.storePreKey(preKeyId, PreKeyRecord(preKeyId, preKey))

        ProtocolPreKeyBundle(
            registrationId = registrationId,
            deviceId = 1,
            preKeyId = preKeyId,
            preKeyPublicBase64 = ProtocolSerialization.toBase64(preKey.publicKey.serialize()),
            signedPreKeyId = signedPreKeyId,
            signedPreKeyPublicBase64 = ProtocolSerialization.toBase64(signedPreKeyRecord.keyPair.publicKey.serialize()),
            signedPreKeySignatureBase64 = ProtocolSerialization.toBase64(signedPreKeyRecord.signature),
            identityKeyBase64 = ProtocolSerialization.toBase64(identityKeyPair.publicKey.publicKey.serialize()),
            kyberPreKeyId = kyberPreKeyId,
            kyberPreKeyPublicBase64 = ProtocolSerialization.toBase64(kyberPreKeyRecord.keyPair.publicKey.serialize()),
            kyberPreKeySignatureBase64 = ProtocolSerialization.toBase64(kyberPreKeyRecord.signature)
        )
    }

    override suspend fun getIdentityFingerprint(identityId: String): String {
        return "Fingerprint for $identityId"
    }
}
