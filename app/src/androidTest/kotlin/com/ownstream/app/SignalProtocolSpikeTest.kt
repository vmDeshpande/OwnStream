package com.ownstream.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.libsignal.protocol.*
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.groups.state.SenderKeyStore
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.*
import java.util.*

@RunWith(AndroidJUnit4::class)
class SignalProtocolSpikeTest {

    @Test
    fun testAliceToBobE2EE() {
        // 1. Setup Identities
        val aliceAddress = SignalProtocolAddress("alice", 1)
        val bobAddress = SignalProtocolAddress("bob", 1)

        val aliceStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), 1111)
        val bobStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), 2222)

        // 2. Bob generates PreKey Bundle
        val bobPreKeyId = 333
        val bobPreKey = ECKeyPair.generate()
        val bobPreKeyRecord = PreKeyRecord(bobPreKeyId, bobPreKey)
        bobStore.storePreKey(bobPreKeyId, bobPreKeyRecord)

        val bobSignedPreKeyId = 444
        val bobSignedPreKey = ECKeyPair.generate()
        val bobSignedPreKeySignature = bobStore.identityKeyPair.privateKey.calculateSignature(bobSignedPreKey.publicKey.serialize())
        val bobSignedPreKeyRecord = SignedPreKeyRecord(bobSignedPreKeyId, System.currentTimeMillis(), bobSignedPreKey, bobSignedPreKeySignature)
        bobStore.storeSignedPreKey(bobSignedPreKeyId, bobSignedPreKeyRecord)

        val bobKyberPreKeyId = 555
        val bobKyberPreKey = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val bobKyberPreKeySignature = bobStore.identityKeyPair.privateKey.calculateSignature(bobKyberPreKey.publicKey.serialize())
        val bobKyberPreKeyRecord = KyberPreKeyRecord(bobKyberPreKeyId, System.currentTimeMillis(), bobKyberPreKey, bobKyberPreKeySignature)
        bobStore.storeKyberPreKey(bobKyberPreKeyId, bobKyberPreKeyRecord)

        val bobBundle = PreKeyBundle(
            bobStore.localRegistrationId,
            bobAddress.deviceId,
            bobPreKeyId,
            bobPreKey.publicKey,
            bobSignedPreKeyId,
            bobSignedPreKey.publicKey,
            bobSignedPreKeySignature,
            bobStore.identityKeyPair.publicKey,
            bobKyberPreKeyId,
            bobKyberPreKey.publicKey,
            bobKyberPreKeySignature
        )

        // 3. Alice establishes session with Bob
        val aliceSessionBuilder = SessionBuilder(aliceStore, bobAddress, aliceAddress)
        aliceSessionBuilder.process(bobBundle)

        assertTrue("Alice should have a session for Bob", aliceStore.containsSession(bobAddress))

        // 4. Alice encrypts a message
        val aliceCipher = SessionCipher(aliceStore, aliceAddress, bobAddress)
        val messageText = "Hello Bob, this is Alice!"
        val ciphertext = aliceCipher.encrypt(messageText.toByteArray())

        assertNotEquals("Ciphertext should not be plaintext", messageText, String(ciphertext.serialize()))
        assertTrue("Ciphertext should be encrypted", ciphertext.type == CiphertextMessage.PREKEY_TYPE || ciphertext.type == CiphertextMessage.WHISPER_TYPE)

        // 5. Bob decrypts the message
        val bobCipher = SessionCipher(bobStore, bobAddress, aliceAddress)
        val decryptedBytes = decryptMessage(bobCipher, ciphertext)
        val decryptedText = String(decryptedBytes)

        assertEquals("Decrypted text should match original", messageText, decryptedText)
        
        // 6. Verify subsequent message
        val aliceCipher2 = SessionCipher(aliceStore, aliceAddress, bobAddress)
        val messageText2 = "Second message"
        val ciphertext2 = aliceCipher2.encrypt(messageText2.toByteArray())
        
        val decryptedBytes2 = decryptMessage(bobCipher, ciphertext2)
        assertEquals("Decrypted second message should match", messageText2, String(decryptedBytes2))
    }

    @Test
    fun testWrongIdentityFails() {
        val aliceAddress = SignalProtocolAddress("alice", 1)
        val bobAddress = SignalProtocolAddress("bob", 1)
        val aliceStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), 1111)
        val bobStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), 2222)
        
        // Bob already has a trusted identity for Alice (an impostor!)
        bobStore.saveIdentity(aliceAddress, IdentityKeyPair.generate().publicKey)
        
        // Minimal session setup
        val bobPreKey = ECKeyPair.generate()
        val bobSignedPreKey = ECKeyPair.generate()
        val bobKyberPreKey = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val bobBundle = PreKeyBundle(2222, 1, 1, bobPreKey.publicKey, 1, bobSignedPreKey.publicKey,
            bobStore.identityKeyPair.privateKey.calculateSignature(bobSignedPreKey.publicKey.serialize()),
            bobStore.identityKeyPair.publicKey, 1, bobKyberPreKey.publicKey,
            bobStore.identityKeyPair.privateKey.calculateSignature(bobKyberPreKey.publicKey.serialize()))
        
        bobStore.storePreKey(1, PreKeyRecord(1, bobPreKey))
        bobStore.storeSignedPreKey(1, SignedPreKeyRecord(1, System.currentTimeMillis(), bobSignedPreKey, bobBundle.signedPreKeySignature))
        bobStore.storeKyberPreKey(1, KyberPreKeyRecord(1, System.currentTimeMillis(), bobKyberPreKey, bobBundle.kyberPreKeySignature))
        
        SessionBuilder(aliceStore, bobAddress, aliceAddress).process(bobBundle)
        val ciphertext = SessionCipher(aliceStore, aliceAddress, bobAddress).encrypt("Hello".toByteArray())
        
        // Bob tries to decrypt, but Alice's identity in the message doesn't match his trusted one
        try {
            SessionCipher(bobStore, bobAddress, aliceAddress).decrypt(ciphertext as PreKeySignalMessage)
            fail("Should have thrown UntrustedIdentityException")
        } catch (e: UntrustedIdentityException) {
            // Success
        }
    }

    private fun decryptMessage(cipher: SessionCipher, ciphertext: CiphertextMessage): ByteArray {
        return when (ciphertext) {
            is PreKeySignalMessage -> cipher.decrypt(ciphertext)
            is SignalMessage -> cipher.decrypt(ciphertext)
            else -> throw IllegalArgumentException("Unknown message type")
        }
    }

    @Test
    fun testSessionPersistenceSimulation() {
        val aliceAddress = SignalProtocolAddress("alice", 1)
        val bobAddress = SignalProtocolAddress("bob", 1)
        
        val aliceStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), 1111)
        val bobStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), 2222)

        // Setup session
        val bobPreKey = ECKeyPair.generate()
        val bobSignedPreKey = ECKeyPair.generate()
        val bobKyberPreKey = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        
        val bobBundle = PreKeyBundle(
            2222, 1, 1, bobPreKey.publicKey, 1, bobSignedPreKey.publicKey,
            bobStore.identityKeyPair.privateKey.calculateSignature(bobSignedPreKey.publicKey.serialize()),
            bobStore.identityKeyPair.publicKey,
            1, bobKyberPreKey.publicKey,
            bobStore.identityKeyPair.privateKey.calculateSignature(bobKyberPreKey.publicKey.serialize())
        )
        
        // Bob needs the keys to decrypt
        bobStore.storePreKey(1, PreKeyRecord(1, bobPreKey))
        bobStore.storeSignedPreKey(1, SignedPreKeyRecord(1, System.currentTimeMillis(), bobSignedPreKey, bobBundle.signedPreKeySignature))
        bobStore.storeKyberPreKey(1, KyberPreKeyRecord(1, System.currentTimeMillis(), bobKyberPreKey, bobBundle.kyberPreKeySignature))

        val aliceBuilder = SessionBuilder(aliceStore, bobAddress, aliceAddress)
        aliceBuilder.process(bobBundle)
        
        // Simulate persistence: serialize session state
        val sessionRecord = aliceStore.loadSession(bobAddress)
        val serializedSession = sessionRecord.serialize()
        
        // Destroy and recreate store
        val aliceStoreNew = InMemorySignalProtocolStore(aliceStore.identityKeyPair, 1111)
        aliceStoreNew.storeSession(bobAddress, SessionRecord(serializedSession))
        
        // Use new store to encrypt
        val aliceCipher = SessionCipher(aliceStoreNew, aliceAddress, bobAddress)
        val ciphertext = aliceCipher.encrypt("Persistent message".toByteArray())
        
        // Recreate Bob cipher with correct address order
        val bobCipher = SessionCipher(bobStore, bobAddress, aliceAddress)
        val decrypted = decryptMessage(bobCipher, ciphertext)
        assertEquals("Persistent message", String(decrypted))
    }

    @Test(expected = Exception::class)
    fun testTamperedCiphertextFails() {
        val aliceAddress = SignalProtocolAddress("alice", 1)
        val bobAddress = SignalProtocolAddress("bob", 1)
        val aliceStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), 1111)
        val bobStore = InMemorySignalProtocolStore(IdentityKeyPair.generate(), 2222)
        
        // Minimal session setup
        val bobPreKey = ECKeyPair.generate()
        val bobSignedPreKey = ECKeyPair.generate()
        val bobKyberPreKey = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val bobBundle = PreKeyBundle(2222, 1, 1, bobPreKey.publicKey, 1, bobSignedPreKey.publicKey,
            bobStore.identityKeyPair.privateKey.calculateSignature(bobSignedPreKey.publicKey.serialize()),
            bobStore.identityKeyPair.publicKey, 1, bobKyberPreKey.publicKey,
            bobStore.identityKeyPair.privateKey.calculateSignature(bobKyberPreKey.publicKey.serialize()))
        
        bobStore.storePreKey(1, PreKeyRecord(1, bobPreKey))
        bobStore.storeSignedPreKey(1, SignedPreKeyRecord(1, System.currentTimeMillis(), bobSignedPreKey, bobBundle.signedPreKeySignature))
        bobStore.storeKyberPreKey(1, KyberPreKeyRecord(1, System.currentTimeMillis(), bobKyberPreKey, bobBundle.kyberPreKeySignature))
        
        SessionBuilder(aliceStore, bobAddress, aliceAddress).process(bobBundle)
        val ciphertext = SessionCipher(aliceStore, aliceAddress, bobAddress).encrypt("Hello".toByteArray())
        
        // Tamper with ciphertext
        val serialized = ciphertext.serialize()
        serialized[serialized.size - 5] = (serialized[serialized.size - 5].toInt() xor 0xFF).toByte()
        
        val tamperedMessage = PreKeySignalMessage(serialized)
        
        SessionCipher(bobStore, bobAddress, aliceAddress).decrypt(tamperedMessage)
    }

    private class InMemorySignalProtocolStore(
        private val initialIdentityKeyPair: IdentityKeyPair,
        private val initialRegistrationId: Int
    ) : SignalProtocolStore {

        private val sessions = mutableMapOf<SignalProtocolAddress, SessionRecord>()
        private val preKeys = mutableMapOf<Int, PreKeyRecord>()
        private val signedPreKeys = mutableMapOf<Int, SignedPreKeyRecord>()
        private val kyberPreKeys = mutableMapOf<Int, KyberPreKeyRecord>()
        private val identities = mutableMapOf<SignalProtocolAddress, IdentityKey>()

        override fun getIdentityKeyPair(): IdentityKeyPair = initialIdentityKeyPair
        override fun getLocalRegistrationId(): Int = initialRegistrationId

        override fun saveIdentity(address: SignalProtocolAddress, identityKey: IdentityKey): IdentityKeyStore.IdentityChange {
            val existing = identities[address]
            return if (existing != identityKey) {
                identities[address] = identityKey
                IdentityKeyStore.IdentityChange.REPLACED_EXISTING
            } else {
                IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
            }
        }

        override fun isTrustedIdentity(
            address: SignalProtocolAddress,
            identityKey: IdentityKey,
            direction: IdentityKeyStore.Direction
        ): Boolean {
            val existing = identities[address]
            return existing == null || existing == identityKey
        }

        override fun getIdentity(address: SignalProtocolAddress): IdentityKey? = identities[address]

        override fun loadSession(address: SignalProtocolAddress): SessionRecord {
            return sessions[address] ?: SessionRecord()
        }

        override fun loadExistingSessions(addresses: MutableList<SignalProtocolAddress>): MutableList<SessionRecord> {
            return addresses.map { loadSession(it) }.toMutableList()
        }

        override fun getSubDeviceSessions(name: String): MutableList<Int> {
            return sessions.keys.filter { it.name == name }.map { it.deviceId }.toMutableList()
        }

        override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
            sessions[address] = record
        }

        override fun containsSession(address: SignalProtocolAddress): Boolean = sessions.containsKey(address)

        override fun deleteSession(address: SignalProtocolAddress) {
            sessions.remove(address)
        }

        override fun deleteAllSessions(name: String) {
            sessions.keys.removeIf { it.name == name }
        }

        override fun loadPreKey(preKeyId: Int): PreKeyRecord {
            return preKeys[preKeyId] ?: throw InvalidKeyIdException("No such prekey: $preKeyId")
        }

        override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
            preKeys[preKeyId] = record
        }

        override fun containsPreKey(preKeyId: Int): Boolean = preKeys.containsKey(preKeyId)

        override fun removePreKey(preKeyId: Int) {
            preKeys.remove(preKeyId)
        }

        override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord {
            return signedPreKeys[signedPreKeyId] ?: throw InvalidKeyIdException("No such signed prekey: $signedPreKeyId")
        }

        override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> = signedPreKeys.values.toMutableList()

        override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
            signedPreKeys[signedPreKeyId] = record
        }

        override fun containsSignedPreKey(signedPreKeyId: Int): Boolean = signedPreKeys.containsKey(signedPreKeyId)

        override fun removeSignedPreKey(signedPreKeyId: Int) {
            signedPreKeys.remove(signedPreKeyId)
        }

        override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord {
            return kyberPreKeys[kyberPreKeyId] ?: throw InvalidKeyIdException("No such kyber prekey: $kyberPreKeyId")
        }

        override fun loadKyberPreKeys(): MutableList<KyberPreKeyRecord> = kyberPreKeys.values.toMutableList()

        override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
            kyberPreKeys[kyberPreKeyId] = record
        }

        override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean = kyberPreKeys.containsKey(kyberPreKeyId)

        override fun markKyberPreKeyUsed(kyberPreKeyId: Int, signedPreKeyId: Int, baseKey: ECPublicKey) {
            kyberPreKeys.remove(kyberPreKeyId)
        }

        override fun storeSenderKey(sender: SignalProtocolAddress, distributionId: UUID, record: SenderKeyRecord) {
            // Not implemented for 1:1 spike
        }

        override fun loadSenderKey(sender: SignalProtocolAddress, distributionId: UUID): SenderKeyRecord? {
            return null
        }
    }
}
