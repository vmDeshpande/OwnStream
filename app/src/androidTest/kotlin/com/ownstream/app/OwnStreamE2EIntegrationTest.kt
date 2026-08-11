package com.ownstream.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ownstream.app.core.crypto.EncryptionManager
import com.ownstream.app.core.crypto.SignalCryptoProvider
import com.ownstream.app.core.crypto.SignalProtocolStoreAdapter
import com.ownstream.app.core.network.MessageTransport
import com.ownstream.protocol.MessageEnvelope
import com.ownstream.protocol.ProtocolPreKeyBundle
import com.ownstream.app.data.local.AppDatabase
import com.ownstream.app.data.local.storage.LocalStorageAdapter
import com.ownstream.app.data.repository.RealChatRepository
import com.ownstream.app.data.repository.RealIdentityRepository
import com.ownstream.app.domain.model.*
import com.ownstream.app.domain.usecase.SendMessageUseCase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OwnStreamE2EIntegrationTest {

    private val encryptionManager = EncryptionManager("test_storage_key")

    @Test
    fun testAliceToBobE2EEFlow() = runBlocking {
        // --- Setup Alice ---
        val aliceDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val aliceStorage = LocalStorageAdapter(aliceDb.conversationDao(), aliceDb.messageDao())
        val aliceChatRepo = RealChatRepository(aliceStorage)
        val aliceIdentityRepo = RealIdentityRepository(aliceDb.identityDao())
        val aliceStoreAdapter = SignalProtocolStoreAdapter(aliceDb.signalDao(), encryptionManager)
        val aliceCrypto = SignalCryptoProvider(aliceStoreAdapter, aliceIdentityRepo)
        
        // --- Setup Bob ---
        val bobDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build()
        val bobStorage = LocalStorageAdapter(bobDb.conversationDao(), bobDb.messageDao())
        val bobChatRepo = RealChatRepository(bobStorage)
        val bobIdentityRepo = RealIdentityRepository(bobDb.identityDao())
        val bobStoreAdapter = SignalProtocolStoreAdapter(bobDb.signalDao(), encryptionManager)
        val bobCrypto = SignalCryptoProvider(bobStoreAdapter, bobIdentityRepo)

        // 1. Create Identities
        val aliceIdentity = aliceCrypto.generateIdentity("Alice")
        aliceIdentityRepo.saveIdentity(aliceIdentity)
        
        val bobIdentity = bobCrypto.generateIdentity("Bob")
        bobIdentityRepo.saveIdentity(bobIdentity)

        // 2. Setup Conversation for both Alice and Bob
        val conversationId = "conv_1"
        val participants = listOf(
            Participant(aliceIdentity.id, "Alice"),
            Participant(bobIdentity.id, "Bob")
        )
        val aliceConv = Conversation(
            id = conversationId,
            title = "Bob",
            storageConfig = StorageConfiguration(conversationId, StorageProviderType.LOCAL),
            participants = participants
        )
        aliceChatRepo.createConversation(aliceConv)
        
        val bobConv = Conversation(
            id = conversationId,
            title = "Alice",
            storageConfig = StorageConfiguration(conversationId, StorageProviderType.LOCAL),
            participants = participants
        )
        bobChatRepo.createConversation(bobConv)

        // 3. Alice establishes session with Bob (Local Bundle Exchange)
        val bobBundle = bobCrypto.getLocalPreKeyBundle()
        aliceCrypto.establishSession(bobIdentity.id, bobBundle)
        
        assertTrue("Alice should have session for Bob", aliceCrypto.hasSession(bobIdentity.id))

        // 4. Alice sends message
        // We need a transport that delivers to Bob's repository
        val mockTransport = object : MessageTransport {
            override suspend fun send(envelope: MessageEnvelope) {
                // Map envelope back to Message for storage on Bob's side
                val message = Message(
                    id = envelope.messageId,
                    conversationId = envelope.conversationId,
                    senderId = envelope.senderId,
                    payload = MessagePayload.Encrypted(envelope.encryptedPayload),
                    timestamp = envelope.timestamp,
                    status = MessageStatus.DELIVERED
                )
                bobChatRepo.sendMessage(message)
            }
            override fun observeIncomingMessages() = kotlinx.coroutines.flow.emptyFlow<MessageEnvelope>()
            override suspend fun publishPreKeyBundle(identityId: String, bundle: ProtocolPreKeyBundle) {}
            override suspend fun fetchPreKeyBundle(identityId: String): ProtocolPreKeyBundle? = null
            override suspend fun connect() {}
            override suspend fun disconnect() {}
        }

        val sendMessageUseCase = SendMessageUseCase(aliceChatRepo, aliceCrypto, mockTransport)
        val aliceMessageText = "Hello Bob, secure world!"
        
        sendMessageUseCase(conversationId, aliceMessageText, aliceIdentity.id)

        // 5. Verify Bob received and can decrypt
        val bobMessages = bobChatRepo.getMessages(conversationId).first()
        assertEquals(1, bobMessages.size)
        
        val receivedMessage = bobMessages.first()
        val payload = receivedMessage.payload
        assertTrue(payload is MessagePayload.Encrypted)
        
        val encryptedPayload = (payload as MessagePayload.Encrypted).encryptedPayload
        assertEquals("SIGNAL_V1", encryptedPayload.algorithm)
        
        // Decrypt on Bob's side
        val decryptedText = bobCrypto.decryptPayload(encryptedPayload, aliceIdentity.id)
        assertEquals(aliceMessageText, decryptedText)

        // 6. Verify second message (ratchet advancement)
        val aliceMessageText2 = "Second secure message"
        sendMessageUseCase(conversationId, aliceMessageText2, aliceIdentity.id)
        
        val bobMessages2 = bobChatRepo.getMessages(conversationId).first()
        assertEquals(2, bobMessages2.size)
        
        val decryptedText2 = bobCrypto.decryptPayload(
            (bobMessages2[1].payload as MessagePayload.Encrypted).encryptedPayload,
            aliceIdentity.id
        )
        assertEquals(aliceMessageText2, decryptedText2)

        aliceDb.close()
        bobDb.close()
    }
}
