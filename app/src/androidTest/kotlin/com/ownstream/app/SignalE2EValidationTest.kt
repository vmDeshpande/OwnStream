package com.ownstream.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ownstream.app.core.crypto.EncryptedPayload
import com.ownstream.app.core.crypto.EncryptionManager
import com.ownstream.app.core.crypto.SignalCryptoProvider
import com.ownstream.app.core.crypto.SignalProtocolStoreAdapter
import com.ownstream.app.data.local.AppDatabase
import com.ownstream.app.data.repository.RealIdentityRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SignalE2EValidationTest {

    private val encryptionManager = EncryptionManager()
    private lateinit var aliceDb: AppDatabase
    private lateinit var bobDb: AppDatabase
    private lateinit var aliceCrypto: SignalCryptoProvider
    private lateinit var bobCrypto: SignalCryptoProvider
    private lateinit var aliceId: String
    private lateinit var bobId: String

    @Before
    fun setup() = runBlocking {
        aliceDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val aliceIdentityRepo = RealIdentityRepository(aliceDb.identityDao())
        val aliceStore = SignalProtocolStoreAdapter(aliceDb.signalDao(), encryptionManager)
        aliceCrypto = SignalCryptoProvider(aliceStore, aliceIdentityRepo)
        val aliceIdentity = aliceCrypto.generateIdentity("Alice")
        aliceIdentityRepo.saveIdentity(aliceIdentity)
        aliceId = aliceIdentity.id

        bobDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val bobIdentityRepo = RealIdentityRepository(bobDb.identityDao())
        val bobStore = SignalProtocolStoreAdapter(bobDb.signalDao(), encryptionManager)
        bobCrypto = SignalCryptoProvider(bobStore, bobIdentityRepo)
        val bobIdentity = bobCrypto.generateIdentity("Bob")
        bobIdentityRepo.saveIdentity(bobIdentity)
        bobId = bobIdentity.id

        // Establish session
        val bobBundle = bobCrypto.getLocalPreKeyBundle()
        aliceCrypto.establishSession(bobId, bobBundle)
    }

    @Test
    fun testSessionPersistenceIntegration() = runBlocking {
        // 1. Alice encrypts
        val message = "Persistence Check"
        val payload = aliceCrypto.encryptPayload(message, listOf(aliceId, bobId))

        // 2. Bob decrypts
        val decrypted = bobCrypto.decryptPayload(payload, aliceId)
        assertEquals(message, decrypted)

        // 3. Destroy and recreate Alice's environment from same DB
        val aliceIdentityRepoNew = RealIdentityRepository(aliceDb.identityDao())
        val aliceStoreNew = SignalProtocolStoreAdapter(aliceDb.signalDao(), encryptionManager)
        val aliceCryptoNew = SignalCryptoProvider(aliceStoreNew, aliceIdentityRepoNew)

        // 4. Alice sends another message (should use same session)
        val message2 = "Message after reload"
        val payload2 = aliceCryptoNew.encryptPayload(message2, listOf(aliceId, bobId))

        // 5. Bob decrypts
        val decrypted2 = bobCrypto.decryptPayload(payload2, aliceId)
        assertEquals(message2, decrypted2)
    }

    @Test
    fun testCiphertextTamperingFails() = runBlocking {
        val payload = aliceCrypto.encryptPayload("Secret", listOf(aliceId, bobId))
        
        // Tamper
        val tamperedData = payload.data.copyOf()
        tamperedData[tamperedData.size - 1] = (tamperedData[tamperedData.size - 1].toInt() xor 0xFF).toByte()
        val tamperedPayload = payload.copy(data = tamperedData)

        try {
            bobCrypto.decryptPayload(tamperedPayload, aliceId)
            fail("Decryption should have failed for tampered data")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun testLegacyMessageCompatibility() = runBlocking {
        val legacyPayload = EncryptedPayload(
            data = "Legacy Plaintext".toByteArray(),
            algorithm = "NONE",
            isEncrypted = false
        )
        
        val decrypted = aliceCrypto.decryptPayload(legacyPayload, "some_sender")
        assertEquals("Legacy Plaintext", decrypted)
    }

    @Test
    fun testEncryptionActuallyEncrypts() = runBlocking {
        val plaintext = "Check if this is hidden"
        val payload = aliceCrypto.encryptPayload(plaintext, listOf(aliceId, bobId))
        
        assertFalse("Payload should not contain plaintext", 
            String(payload.data).contains(plaintext))
    }
}
