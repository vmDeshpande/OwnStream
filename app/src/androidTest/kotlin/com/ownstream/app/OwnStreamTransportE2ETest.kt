package com.ownstream.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ownstream.app.core.crypto.*
import com.ownstream.app.core.network.InMemoryMessageTransport
import com.ownstream.app.data.local.AppDatabase
import com.ownstream.protocol.EncryptedPayload
import com.ownstream.protocol.MessageEnvelope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyStore

@RunWith(AndroidJUnit4::class)
class OwnStreamTransportE2ETest {

    private lateinit var database: AppDatabase
    private val keyStoreProvider = object : KeyStoreProvider {
        override fun getKeyStore(): KeyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }
    private val encryptionManager = EncryptionManager("test_storage_key", keyStoreProvider)
    private lateinit var storeA: SignalProtocolStoreAdapter
    private lateinit var storeB: SignalProtocolStoreAdapter
    private lateinit var transportA: InMemoryMessageTransport
    private lateinit var transportB: InMemoryMessageTransport

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        
        storeA = SignalProtocolStoreAdapter(database.signalDao(), encryptionManager)
        storeB = SignalProtocolStoreAdapter(database.signalDao(), encryptionManager)
        
        val relay = InMemoryMessageTransport.Relay()
        transportA = InMemoryMessageTransport("os_alice1234", relay)
        transportB = InMemoryMessageTransport("os_bob1234567", relay)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testTransportRouting() = runBlocking {
        val envelope = MessageEnvelope(
            messageId = "msg1",
            conversationId = "conv1",
            senderId = "os_alice1234",
            recipientId = "os_bob1234567",
            timestamp = 1000L,
            encryptedPayload = EncryptedPayload("QUFB", "SIGNAL_V1", true)
        )
        
        transportA.send(envelope)
        val received = transportB.observeIncomingMessages().first()
        assertEquals("msg1", received.messageId)
    }
}
