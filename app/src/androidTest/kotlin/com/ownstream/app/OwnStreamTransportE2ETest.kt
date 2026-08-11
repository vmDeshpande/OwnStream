package com.ownstream.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ownstream.app.core.crypto.EncryptionManager
import com.ownstream.app.core.crypto.SignalCryptoProvider
import com.ownstream.app.core.crypto.SignalProtocolStoreAdapter
import com.ownstream.app.core.network.InMemoryMessageTransport
import com.ownstream.protocol.MessageEnvelope
import com.ownstream.app.data.local.AppDatabase
import com.ownstream.app.data.local.storage.LocalStorageAdapter
import com.ownstream.app.data.repository.RealChatRepository
import com.ownstream.app.data.repository.RealIdentityRepository
import com.ownstream.app.domain.model.*
import com.ownstream.app.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnStreamTransportE2ETest {

    private val encryptionManager = EncryptionManager("test_storage_key")

    @Test
    fun testAliceToBobViaRelayE2EE() = runBlocking {
        val relay = InMemoryMessageTransport.Relay()

        // --- Alice Environment ---
        val aliceDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val aliceIdentityRepo = RealIdentityRepository(aliceDb.identityDao())
        val aliceCrypto = SignalCryptoProvider(SignalProtocolStoreAdapter(aliceDb.signalDao(), encryptionManager), aliceIdentityRepo)
        val aliceIdentity = aliceCrypto.generateIdentity("Alice")
        aliceIdentityRepo.saveIdentity(aliceIdentity)
        
        val aliceTransport = InMemoryMessageTransport(aliceIdentity.id, relay)
        val aliceChatRepo = RealChatRepository(LocalStorageAdapter(aliceDb.conversationDao(), aliceDb.messageDao()))
        val aliceSendMessage = SendMessageUseCase(aliceChatRepo, aliceCrypto, aliceTransport)

        // --- Bob Environment ---
        val bobDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val bobIdentityRepo = RealIdentityRepository(bobDb.identityDao())
        val bobCrypto = SignalCryptoProvider(SignalProtocolStoreAdapter(bobDb.signalDao(), encryptionManager), bobIdentityRepo)
        val bobIdentity = bobCrypto.generateIdentity("Bob")
        bobIdentityRepo.saveIdentity(bobIdentity)

        val bobTransport = InMemoryMessageTransport(bobIdentity.id, relay)

        // --- Relay Simulation Hook ---
        // Route messages from relay bus to transports
        val relayJob = launch {
            relay.observeMessages().collect { envelope ->
                aliceTransport.receiveFromBus(envelope)
                bobTransport.receiveFromBus(envelope)
            }
        }

        // 1. Bob publishes his PreKey Bundle
        val bobBundle = bobCrypto.getLocalPreKeyBundle()
        bobTransport.publishPreKeyBundle(bobIdentity.id, bobBundle)

        // 2. Alice fetches Bob's bundle and establishes session
        val fetchedBundle = aliceTransport.fetchPreKeyBundle(bobIdentity.id)
        assertNotNull(fetchedBundle)
        aliceCrypto.establishSession(bobIdentity.id, fetchedBundle!!)

        // 3. Setup Alice's view of the conversation
        val conversationId = "relay_conv_1"
        aliceChatRepo.createConversation(Conversation(
            id = conversationId,
            title = "Bob",
            storageConfig = StorageConfiguration(conversationId, StorageProviderType.LOCAL),
            participants = listOf(Participant(aliceIdentity.id, "Alice"), Participant(bobIdentity.id, "Bob"))
        ))

        // 4. Alice sends message via Transport
        val messageText = "Hi Bob, I'm sending this through the relay!"
        aliceSendMessage(conversationId, messageText, aliceIdentity.id)

        // 5. Bob receives from transport
        val receivedEnvelope = withTimeout(2000L) {
            bobTransport.observeIncomingMessages().first()
        }
        
        assertNotNull(receivedEnvelope)
        assertEquals(aliceIdentity.id, receivedEnvelope.senderId)
        
        // 6. Bob decrypts
        val decrypted = bobCrypto.decryptPayload(receivedEnvelope.encryptedPayload, aliceIdentity.id)
        assertEquals(messageText, decrypted)

        relayJob.cancel()
        aliceDb.close()
        bobDb.close()
    }

    @Test
    fun testOfflineMessageDelivery() = runBlocking {
        val relay = InMemoryMessageTransport.Relay()

        // Setup Alice
        val aliceDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val aliceIdentityRepo = RealIdentityRepository(aliceDb.identityDao())
        val aliceCrypto = SignalCryptoProvider(SignalProtocolStoreAdapter(aliceDb.signalDao(), encryptionManager), aliceIdentityRepo)
        val aliceIdentity = aliceCrypto.generateIdentity("Alice")
        aliceIdentityRepo.saveIdentity(aliceIdentity)
        val aliceTransport = InMemoryMessageTransport(aliceIdentity.id, relay)
        val aliceSendMessage = SendMessageUseCase(RealChatRepository(LocalStorageAdapter(aliceDb.conversationDao(), aliceDb.messageDao())), aliceCrypto, aliceTransport)

        // Setup Bob (offline, no transport instance yet)
        val bobDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val bobIdentityRepo = RealIdentityRepository(bobDb.identityDao())
        val bobCrypto = SignalCryptoProvider(SignalProtocolStoreAdapter(bobDb.signalDao(), encryptionManager), bobIdentityRepo)
        val bobIdentity = bobCrypto.generateIdentity("Bob")
        bobIdentityRepo.saveIdentity(bobIdentity)

        // 1. Establish session (Alice needs bundle)
        val bobBundle = bobCrypto.getLocalPreKeyBundle()
        relay.publishBundle(bobIdentity.id, bobBundle)
        aliceCrypto.establishSession(bobIdentity.id, aliceTransport.fetchPreKeyBundle(bobIdentity.id)!!)

        // 2. Alice sends message while Bob is offline
        val conversationId = "offline_conv"
        // Mock Alice knowing the conversation
        val aliceChatRepo = RealChatRepository(LocalStorageAdapter(aliceDb.conversationDao(), aliceDb.messageDao()))
        aliceChatRepo.createConversation(Conversation(
            id = conversationId,
            title = "Bob",
            storageConfig = StorageConfiguration(conversationId, StorageProviderType.LOCAL),
            participants = listOf(Participant(aliceIdentity.id, "Alice"), Participant(bobIdentity.id, "Bob"))
        ))
        
        val offlineMessage = "This should be queued"
        aliceSendMessage(conversationId, offlineMessage, aliceIdentity.id)

        // 3. Bob comes online
        val bobTransport = InMemoryMessageTransport(bobIdentity.id, relay)
        bobTransport.connect() // Should trigger offline message fetch

        // 4. Verify delivery
        val received = withTimeout(2000L) {
            bobTransport.observeIncomingMessages().first()
        }
        
        val decrypted = bobCrypto.decryptPayload(received.encryptedPayload, aliceIdentity.id)
        assertEquals(offlineMessage, decrypted)

        aliceDb.close()
        bobDb.close()
    }

    @Test
    fun testRelaySecurityBlindness() = runBlocking {
        val plaintext = "Sensitive Content"
        
        // Alice
        val aliceDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val aliceIdentityRepo = RealIdentityRepository(aliceDb.identityDao())
        val aliceCrypto = SignalCryptoProvider(SignalProtocolStoreAdapter(aliceDb.signalDao(), encryptionManager), aliceIdentityRepo)
        val aliceIdentity = aliceCrypto.generateIdentity("Alice")
        aliceIdentityRepo.saveIdentity(aliceIdentity)
        
        // Bob
        val bobDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val bobIdentityRepo = RealIdentityRepository(bobDb.identityDao())
        val bobCrypto = SignalCryptoProvider(SignalProtocolStoreAdapter(bobDb.signalDao(), encryptionManager), bobIdentityRepo)
        val bobIdentity = bobCrypto.generateIdentity("Bob")
        bobIdentityRepo.saveIdentity(bobIdentity)

        // Alice establishes session
        aliceCrypto.establishSession(bobIdentity.id, bobCrypto.getLocalPreKeyBundle())

        val payload = aliceCrypto.encryptPayload(plaintext, listOf(aliceIdentity.id, bobIdentity.id))
        
        val envelope = MessageEnvelope(
            messageId = "1",
            conversationId = "c1",
            senderId = aliceIdentity.id,
            recipientId = bobIdentity.id,
            timestamp = System.currentTimeMillis(),
            encryptedPayload = payload
        )

        // The Relay only sees the envelope
        val serializedPayload = envelope.encryptedPayload.data
        assertFalse("Relay should not see plaintext in encrypted payload", 
            String(serializedPayload).contains(plaintext))
        
        aliceDb.close()
        bobDb.close()
    }
}
