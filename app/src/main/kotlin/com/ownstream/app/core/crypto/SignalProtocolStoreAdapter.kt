package com.ownstream.app.core.crypto

import com.ownstream.app.data.local.*
import kotlinx.coroutines.runBlocking
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.state.*
import java.util.*
import javax.inject.Inject

/**
 * Adapter that connects libsignal's persistence interfaces to Room database.
 * Uses EncryptionManager to protect sensitive data at rest.
 */
class SignalProtocolStoreAdapter @Inject constructor(
    private val signalDao: SignalDao,
    private val encryptionManager: EncryptionManager
) : SignalProtocolStore {

    // IdentityKeyStore

    override fun getIdentityKeyPair(): IdentityKeyPair {
        return runBlocking {
            val entity = signalDao.getLocalIdentity()
            if (entity != null) {
                val decrypted = encryptionManager.decrypt(entity.encryptedIdentityKeyPair)
                IdentityKeyPair(decrypted)
            } else {
                // Should be generated during onboarding, but fallback to fresh one for now if needed
                // in real app this might throw or handle carefully
                val newKeyPair = IdentityKeyPair.generate()
                saveLocalIdentity(0, newKeyPair) // registrationId 0 as placeholder
                newKeyPair
            }
        }
    }

    override fun getLocalRegistrationId(): Int {
        return runBlocking {
            signalDao.getLocalIdentity()?.registrationId ?: 0
        }
    }

    override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): IdentityKeyStore.IdentityChange {
        return runBlocking {
            val existing = signalDao.getTrustedIdentity(address.name, address.deviceId)
            if (existing == null) {
                signalDao.insertTrustedIdentity(
                    SignalTrustedIdentityEntity(address.name, address.deviceId, identityKey.publicKey.serialize(), 1)
                )
                IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
            } else {
                val existingKey = existing.identityKey
                if (!existingKey.contentEquals(identityKey.publicKey.serialize())) {
                    signalDao.insertTrustedIdentity(
                        SignalTrustedIdentityEntity(address.name, address.deviceId, identityKey.publicKey.serialize(), 2) // 2 as CHANGED/UNTRUSTED
                    )
                    IdentityKeyStore.IdentityChange.REPLACED_EXISTING
                } else {
                    IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
                }
            }
        }
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction
    ): Boolean {
        return runBlocking {
            val existing = signalDao.getTrustedIdentity(address.name, address.deviceId)
            if (existing == null) return@runBlocking true
            if (!existing.identityKey.contentEquals(identityKey.publicKey.serialize())) return@runBlocking false
            existing.trustLevel == 1 // 1 for TRUSTED
        }
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? {
        return runBlocking {
            signalDao.getTrustedIdentity(address.name, address.deviceId)?.let {
                IdentityKey(it.identityKey, 0)
            }
        }
    }

    suspend fun saveLocalIdentity(registrationId: Int, keyPair: IdentityKeyPair) {
        val encrypted = encryptionManager.encrypt(keyPair.serialize())
        signalDao.insertLocalIdentity(SignalIdentityEntity(1, registrationId, encrypted))
    }

    // SessionStore

    override fun loadSession(address: SignalProtocolAddress): SessionRecord {
        return runBlocking {
            signalDao.getSession(address.name, address.deviceId)?.let {
                val decrypted = encryptionManager.decrypt(it.encryptedSessionRecord)
                SessionRecord(decrypted)
            } ?: SessionRecord()
        }
    }

    override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>): MutableList<SessionRecord> {
        return addresses.map { loadSession(it) }.toMutableList()
    }

    override fun getSubDeviceSessions(name: String): MutableList<Int> {
        return runBlocking {
            signalDao.getSessionsForName(name).map { it.deviceId }.toMutableList()
        }
    }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        runBlocking {
            val encrypted = encryptionManager.encrypt(record.serialize())
            signalDao.insertSession(SignalSessionEntity(address.name, address.deviceId, encrypted))
        }
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean {
        return runBlocking {
            signalDao.getSession(address.name, address.deviceId) != null
        }
    }

    override fun deleteSession(address: SignalProtocolAddress) {
        runBlocking {
            signalDao.deleteSession(address.name, address.deviceId)
        }
    }

    override fun deleteAllSessions(name: String) {
        runBlocking {
            signalDao.deleteSessionsForName(name)
        }
    }

    // PreKeyStore

    override fun loadPreKey(preKeyId: Int): PreKeyRecord {
        return runBlocking {
            signalDao.getPreKey(preKeyId)?.let {
                val decrypted = encryptionManager.decrypt(it.encryptedRecord)
                PreKeyRecord(decrypted)
            } ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No such prekey: $preKeyId")
        }
    }

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        runBlocking {
            val encrypted = encryptionManager.encrypt(record.serialize())
            signalDao.insertPreKey(SignalPreKeyEntity(preKeyId, encrypted))
        }
    }

    override fun containsPreKey(preKeyId: Int): Boolean {
        return runBlocking {
            signalDao.hasPreKey(preKeyId)
        }
    }

    override fun removePreKey(preKeyId: Int) {
        runBlocking {
            signalDao.deletePreKey(preKeyId)
        }
    }

    // SignedPreKeyStore

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
        return runBlocking {
            signalDao.getSignedPreKey(signedPreKeyId)?.let {
                val decrypted = encryptionManager.decrypt(it.encryptedRecord)
                SignedPreKeyRecord(decrypted)
            } ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No such signed prekey: $signedPreKeyId")
        }
    }

    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> {
        return runBlocking {
            signalDao.getAllSignedPreKeys().map {
                val decrypted = encryptionManager.decrypt(it.encryptedRecord)
                SignedPreKeyRecord(decrypted)
            }.toMutableList()
        }
    }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        runBlocking {
            val encrypted = encryptionManager.encrypt(record.serialize())
            signalDao.insertSignedPreKey(SignalSignedPreKeyEntity(signedPreKeyId, encrypted))
        }
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean {
        return runBlocking {
            signalDao.getSignedPreKey(signedPreKeyId) != null
        }
    }

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        runBlocking {
            signalDao.deleteSignedPreKey(signedPreKeyId)
        }
    }

    // KyberPreKeyStore

    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
        return runBlocking {
            signalDao.getKyberPreKey(kyberPreKeyId)?.let {
                val decrypted = encryptionManager.decrypt(it.encryptedRecord)
                KyberPreKeyRecord(decrypted)
            } ?: throw org.signal.libsignal.protocol.InvalidKeyIdException("No such kyber prekey: $kyberPreKeyId")
        }
    }

    override fun loadKyberPreKeys(): MutableList<KyberPreKeyRecord> {
        return runBlocking {
            signalDao.getAllKyberPreKeys().map {
                val decrypted = encryptionManager.decrypt(it.encryptedRecord)
                KyberPreKeyRecord(decrypted)
            }.toMutableList()
        }
    }

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
        runBlocking {
            val encrypted = encryptionManager.encrypt(record.serialize())
            signalDao.insertKyberPreKey(SignalKyberPreKeyEntity(kyberPreKeyId, encrypted))
        }
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean {
        return runBlocking {
            signalDao.getKyberPreKey(kyberPreKeyId) != null
        }
    }

    override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) {
        runBlocking {
            // Minimal implementation: delete it if it's a one-time key
            signalDao.deleteKyberPreKey(kyberPreKeyId)
        }
    }

    // SenderKeyStore (Unused for 1:1, but required by interface)

    override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
        // No-op for now
    }

    override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? {
        return null
    }
}
