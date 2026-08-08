package com.ownstream.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ownstream.app.core.crypto.EncryptionManager
import com.ownstream.app.core.crypto.SignalProtocolStoreAdapter
import com.ownstream.app.data.local.AppDatabase
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.signal.libsignal.protocol.*
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.*

@RunWith(AndroidJUnit4::class)
class SignalPersistenceTest {

    private lateinit var database: AppDatabase
    private lateinit var encryptionManager: EncryptionManager
    private lateinit var adapter: SignalProtocolStoreAdapter

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        encryptionManager = EncryptionManager() // Uses real Android Keystore
        adapter = SignalProtocolStoreAdapter(database.signalDao(), encryptionManager)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testEncryptionManagerRoundTrip() {
        val original = "Sensitive Data".toByteArray()
        val encrypted = encryptionManager.encrypt(original)
        assertFalse("Ciphertext should not contain plaintext", original.contentEquals(encrypted))
        
        val decrypted = encryptionManager.decrypt(encrypted)
        assertArrayEquals(original, decrypted)
    }

    @Test
    fun testIdentityPersistence() {
        val originalKeyPair = IdentityKeyPair.generate()
        val registrationId = 1234
        
        runBlockingTest {
            adapter.saveLocalIdentity(registrationId, originalKeyPair)
        }
        
        // Destroy adapter and recreate (simulated)
        val newAdapter = SignalProtocolStoreAdapter(database.signalDao(), encryptionManager)
        
        assertEquals(registrationId, newAdapter.localRegistrationId)
        val reloadedKeyPair = newAdapter.identityKeyPair
        assertArrayEquals(originalKeyPair.serialize(), reloadedKeyPair.serialize())
    }

    @Test
    fun testSessionPersistence() {
        val address = SignalProtocolAddress("bob", 1)
        val originalRecord = SessionRecord()
        // Mutate record to ensure we are saving state
        originalRecord.archiveCurrentState()
        
        adapter.storeSession(address, originalRecord)
        
        val newAdapter = SignalProtocolStoreAdapter(database.signalDao(), encryptionManager)
        assertTrue(newAdapter.containsSession(address))
        
        val reloadedRecord = newAdapter.loadSession(address)
        assertArrayEquals(originalRecord.serialize(), reloadedRecord.serialize())
    }

    @Test
    fun testPreKeyConsumption() {
        val preKeyId = 101
        val record = PreKeyRecord(preKeyId, ECKeyPair.generate())
        
        adapter.storePreKey(preKeyId, record)
        assertTrue(adapter.containsPreKey(preKeyId))
        
        adapter.removePreKey(preKeyId)
        assertFalse(adapter.containsPreKey(preKeyId))
    }

    @Test
    fun testKyberPreKeyPersistence() {
        val id = 202
        val record = KyberPreKeyRecord(id, System.currentTimeMillis(), KEMKeyPair.generate(KEMKeyType.KYBER_1024), ByteArray(64))
        
        adapter.storeKyberPreKey(id, record)
        assertTrue(adapter.containsKyberPreKey(id))
        
        val reloaded = adapter.loadKyberPreKey(id)
        assertArrayEquals(record.serialize(), reloaded.serialize())
    }

    @Test
    fun testTrustStateBehavior() {
        val address = SignalProtocolAddress("alice", 1)
        val identityKey1 = IdentityKeyPair.generate().publicKey
        val identityKey2 = IdentityKeyPair.generate().publicKey
        
        // 1. New identity
        val change1 = adapter.saveIdentity(address, identityKey1)
        assertEquals(IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED, change1)
        assertTrue(adapter.isTrustedIdentity(address, identityKey1, IdentityKeyStore.Direction.SENDING))
        
        // 2. Unchanged identity
        val change2 = adapter.saveIdentity(address, identityKey1)
        assertEquals(IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED, change2)
        
        // 3. Replaced identity
        val change3 = adapter.saveIdentity(address, identityKey2)
        assertEquals(IdentityKeyStore.IdentityChange.REPLACED_EXISTING, change3)
        // Note: Our implementation marks it as untrusted if it changed (trustLevel 2)
        assertFalse("Should be untrusted after change in our spike implementation", adapter.isTrustedIdentity(address, identityKey2, IdentityKeyStore.Direction.SENDING))
    }

    @Test
    fun testSecurityAuditPlaintextNotStored() {
        val originalKeyPair = IdentityKeyPair.generate()
        runBlockingTest {
            adapter.saveLocalIdentity(999, originalKeyPair)
        }
        
        // Query database directly
        val rawDb = database.openHelper.readableDatabase
        val cursor = rawDb.query("SELECT encryptedIdentityKeyPair FROM signal_identities WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        val encryptedBlob = cursor.getBlob(0)
        
        // The encrypted blob should NOT be the same as the serialized key pair
        val serialized = originalKeyPair.serialize()
        assertFalse("Database should NOT contain plaintext serialized keys", 
            containsSubarray(encryptedBlob, serialized))
    }

    @Test
    fun testFullSignalRoomIntegration() {
        // Alice setup
        val aliceAddress = SignalProtocolAddress("alice", 1)
        val aliceDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val aliceAdapter = SignalProtocolStoreAdapter(aliceDb.signalDao(), encryptionManager)
        val aliceKeyPair = IdentityKeyPair.generate()
        runBlockingTest { aliceAdapter.saveLocalIdentity(1111, aliceKeyPair) }

        // Bob setup
        val bobAddress = SignalProtocolAddress("bob", 1)
        val bobDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val bobAdapter = SignalProtocolStoreAdapter(bobDb.signalDao(), encryptionManager)
        val bobKeyPair = IdentityKeyPair.generate()
        runBlockingTest { bobAdapter.saveLocalIdentity(2222, bobKeyPair) }

        // 1. Bob generates bundle
        val bobPreKey = ECKeyPair.generate()
        val bobSignedPreKey = ECKeyPair.generate()
        val bobKyberPreKey = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val bobSignedPreKeySig = bobKeyPair.privateKey.calculateSignature(bobSignedPreKey.publicKey.serialize())
        val bobKyberPreKeySig = bobKeyPair.privateKey.calculateSignature(bobKyberPreKey.publicKey.serialize())

        bobAdapter.storePreKey(1, PreKeyRecord(1, bobPreKey))
        bobAdapter.storeSignedPreKey(1, SignedPreKeyRecord(1, System.currentTimeMillis(), bobSignedPreKey, bobSignedPreKeySig))
        bobAdapter.storeKyberPreKey(1, KyberPreKeyRecord(1, System.currentTimeMillis(), bobKyberPreKey, bobKyberPreKeySig))

        val bobBundle = PreKeyBundle(
            2222, 1, 1, bobPreKey.publicKey, 1, bobSignedPreKey.publicKey, bobSignedPreKeySig,
            bobKeyPair.publicKey, 1, bobKyberPreKey.publicKey, bobKyberPreKeySig
        )

        // 2. Alice establishes session
        SessionBuilder(aliceAdapter, bobAddress, aliceAddress).process(bobBundle)
        
        // 3. Alice encrypts "Hello Bob"
        val aliceCipher = SessionCipher(aliceAdapter, aliceAddress, bobAddress)
        val ciphertext = aliceCipher.encrypt("Hello Bob".toByteArray())
        
        // 4. Bob decrypts
        val bobCipher = SessionCipher(bobAdapter, bobAddress, aliceAddress)
        val decrypted = decryptMessage(bobCipher, ciphertext)
        assertEquals("Hello Bob", String(decrypted))
        
        // 5. Simulate App Restart: Recreate adapters from SAME databases
        val aliceAdapterNew = SignalProtocolStoreAdapter(aliceDb.signalDao(), encryptionManager)
        val bobAdapterNew = SignalProtocolStoreAdapter(bobDb.signalDao(), encryptionManager)
        
        // 6. Alice sends "Second message"
        val aliceCipherNew = SessionCipher(aliceAdapterNew, aliceAddress, bobAddress)
        val ciphertext2 = aliceCipherNew.encrypt("Second message".toByteArray())
        
        // 7. Bob decrypts with new adapter
        val bobCipherNew = SessionCipher(bobAdapterNew, bobAddress, aliceAddress)
        val decrypted2 = decryptMessage(bobCipherNew, ciphertext2)
        assertEquals("Second message", String(decrypted2))
        
        aliceDb.close()
        bobDb.close()
    }

    private fun decryptMessage(cipher: SessionCipher, ciphertext: CiphertextMessage): ByteArray {
        return when (ciphertext) {
            is PreKeySignalMessage -> cipher.decrypt(ciphertext)
            is SignalMessage -> cipher.decrypt(ciphertext)
            else -> throw IllegalArgumentException("Unknown message type")
        }
    }

    private fun runBlockingTest(block: suspend () -> Unit) {
        kotlinx.coroutines.runBlocking { block() }
    }
    
    private fun containsSubarray(array: ByteArray, subarray: ByteArray): Boolean {
        if (subarray.isEmpty()) return true
        if (array.size < subarray.size) return false
        for (i in 0..array.size - subarray.size) {
            var found = true
            for (j in subarray.indices) {
                if (array[i + j] != subarray[j]) {
                    found = false
                    break
                }
            }
            if (found) return true
        }
        return false
    }
}
