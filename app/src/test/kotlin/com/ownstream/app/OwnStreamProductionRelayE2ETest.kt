package com.ownstream.app

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.ownstream.app.core.crypto.*
import com.ownstream.app.core.network.*
import com.ownstream.app.core.storage.SecureStorage
import com.ownstream.app.data.local.AppDatabase
import com.ownstream.app.data.repository.RealIdentityRepository
import com.ownstream.protocol.*
import com.ownstream.relay.module
import io.ktor.client.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.websocket.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OwnStreamProductionRelayE2ETest {

    private val TAG = "6E-Test"
    private lateinit var relayServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private val relayPort = 9091
    private val relayBaseUrl = "http://127.0.0.1:$relayPort"
    private val relayWsUrl = "ws://127.0.0.1:$relayPort/v1/ws"

    private lateinit var aliceEnv: TestDeviceEnvironment
    private lateinit var bobEnv: TestDeviceEnvironment

    @Before
    fun setup() {
        ShadowLog.stream = System.out
        println("[6E] Starting setup...")
        runBlocking {
            // 1. Start Real Ktor Relay
            println("[6E] Starting relay server on $relayPort...")
            relayServer = embeddedServer(CIO, port = relayPort, host = "127.0.0.1") {
                module()
            }.start(wait = false)
            
            // Wait for server to be ready
            delay(2000)
            println("[6E] Relay server should be up.")

            val context = ApplicationProvider.getApplicationContext<Context>()

            // 2. Setup Alice's independent environment
            println("[6E] Setting up Alice environment...")
            aliceEnv = TestDeviceEnvironment(context, "os_alice", "alice_storage_key", "alice_identity_key", relayBaseUrl, relayWsUrl)
            
            // 3. Setup Bob's independent environment
            println("[6E] Setting up Bob environment...")
            bobEnv = TestDeviceEnvironment(context, "os_bob", "bob_storage_key", "bob_identity_key", relayBaseUrl, relayWsUrl)
        }
        println("[6E] Setup complete.")
    }

    @After
    fun tearDown() {
        println("[6E] Tearing down...")
        aliceEnv.cleanup()
        bobEnv.cleanup()
        relayServer.stop(1000, 1000)
        println("[6E] Teardown complete.")
    }

    @Test
    fun testAliceAndBobFullE2E() = runBlocking {
        withTimeout(120000) { // 2 minutes total test timeout
            println("[6E] Starting testAliceAndBobFullE2E")

            // A. Setup Identities and Publish PreKeys
            println("[6E] Setting up Alice identity...")
            aliceEnv.setupIdentity("Alice")
            println("[6E] Alice identity set up.")
            println("[6E] Setting up Bob identity...")
            bobEnv.setupIdentity("Bob")
            println("[6E] Bob identity set up.")

            println("[6E] Publishing Alice prekeys...")
            aliceEnv.transport.publishPreKeyBundle(aliceEnv.ownStreamId, aliceEnv.crypto.getLocalPreKeyBundle())
            println("[6E] Alice prekeys published.")
            println("[6E] Publishing Bob prekeys...")
            bobEnv.transport.publishPreKeyBundle(bobEnv.ownStreamId, bobEnv.crypto.getLocalPreKeyBundle())
            println("[6E] Bob prekeys published.")

            // B. Connect to WebSockets
            println("[6E] Connecting Alice WebSocket...")
            val aliceJob = launch { 
                println("[6E-A] Alice connect coroutine started")
                aliceEnv.transport.connect(aliceEnv.ownStreamId) 
                println("[6E-A] Alice connect coroutine finished")
            }
            println("[6E] Alice connect job launched.")
            println("[6E] Connecting Bob WebSocket...")
            val bobJob = launch { 
                println("[6E-B] Bob connect coroutine started")
                bobEnv.transport.connect(bobEnv.ownStreamId) 
                println("[6E-B] Bob connect coroutine finished")
            }
            println("[6E] Bob connect job launched.")
            
            println("[6E] Waiting for connections to stabilize...")
            delay(10000) 
            println("[6E] After delay, checking if connections are active...")

            // C. Alice establishes session with Bob
            println("[6E] Alice fetching Bob's prekey bundle...")
            val bobBundle = withTimeout(10000) {
                aliceEnv.transport.fetchPreKeyBundle(bobEnv.ownStreamId)
            }
            println("[6E] Alice fetched Bob's bundle: $bobBundle")
            assertNotNull("Alice should be able to fetch Bob's bundle", bobBundle)
            println("[6E] Alice establishing Signal session with Bob...")
            aliceEnv.crypto.establishSession(bobEnv.ownStreamId, bobBundle!!)
            println("[6E] Alice session established.")

            // D. Alice sends message to Bob
            val aliceToBobText = "Hello from Alice through the real relay!"
            println("[6E] Alice encrypting message for Bob...")
            val aliceEncrypted = aliceEnv.crypto.encryptPayload(aliceToBobText, listOf(bobEnv.ownStreamId))
            val aliceEnvelope = MessageEnvelope(
                messageId = "msg_a_to_b_" + UUID.randomUUID().toString().take(6),
                conversationId = "conv_shared",
                senderId = aliceEnv.ownStreamId,
                recipientId = bobEnv.ownStreamId,
                timestamp = System.currentTimeMillis(),
                encryptedPayload = aliceEncrypted
            )

            println("[6E] Alice sending message envelope...")
            aliceEnv.transport.send(aliceEnvelope)
            println("[6E] Alice message sent.")

            // E. Bob receives and decrypts
            println("[6E] Bob waiting for incoming message...")
            val bobReceived = withTimeout(20000) {
                bobEnv.transport.observeIncomingMessages().first()
            }
            println("[6E] Bob received messageId: ${bobReceived.messageId}")
            assertEquals(aliceEnvelope.messageId, bobReceived.messageId)
            
            println("[6E] Bob decrypting message...")
            val bobDecrypted = bobEnv.crypto.decryptPayload(bobReceived.encryptedPayload, aliceEnv.ownStreamId)
            println("[6E] Bob decrypted: '$bobDecrypted'")
            assertEquals(aliceToBobText, bobDecrypted)

            // F. Bob replies to Alice
            val bobToAliceText = "Hi Alice! Received your message. E2EE works!"
            println("[6E] Bob sending reply to Alice...")
            
            assertTrue("Bob should now have a session with Alice", bobEnv.crypto.hasSession(aliceEnv.ownStreamId))
            
            val bobEncrypted = bobEnv.crypto.encryptPayload(bobToAliceText, listOf(aliceEnv.ownStreamId))
            val bobEnvelope = MessageEnvelope(
                messageId = "msg_b_to_a_" + UUID.randomUUID().toString().take(6),
                conversationId = "conv_shared",
                senderId = bobEnv.ownStreamId,
                recipientId = aliceEnv.ownStreamId,
                timestamp = System.currentTimeMillis(),
                encryptedPayload = bobEncrypted
            )

            bobEnv.transport.send(bobEnvelope)
            println("[6E] Bob reply sent.")

            // G. Alice receives and decrypts reply
            println("[6E] Alice waiting for reply...")
            val aliceReceived = withTimeout(20000) {
                aliceEnv.transport.observeIncomingMessages().first()
            }
            println("[6E] Alice received messageId: ${aliceReceived.messageId}")
            assertEquals(bobEnvelope.messageId, aliceReceived.messageId)
            
            println("[6E] Alice decrypting reply...")
            val aliceDecrypted = aliceEnv.crypto.decryptPayload(aliceReceived.encryptedPayload, bobEnv.ownStreamId)
            println("[6E] Alice decrypted: '$aliceDecrypted'")
            assertEquals(bobToAliceText, aliceDecrypted)

            aliceJob.cancel()
            bobJob.cancel()
            println("[6E] Test finished successfully!")
        }
    }

    class TestDeviceEnvironment(
        val context: Context,
        val ownStreamId: String,
        val storageKeyAlias: String,
        val identityKeyAlias: String,
        val relayBaseUrl: String,
        val relayWsUrl: String
    ) {
        companion object {
            val commonKeyStore: KeyStore = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
        }

        val mockKeyStoreProvider = object : KeyStoreProvider {
            override fun getKeyStore(): KeyStore = commonKeyStore
        }

        val database: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val encryptionManager = EncryptionManager(storageKeyAlias, mockKeyStoreProvider)
        val storeAdapter = SignalProtocolStoreAdapter(database.signalDao(), encryptionManager)
        
        private val identityDao = database.identityDao()
        val identityRepository = RealIdentityRepository(identityDao)
        
        val crypto = SignalCryptoProvider(storeAdapter, identityRepository)
        
        val secureStorage = object : SecureStorage {
            private val prefs = mutableMapOf<String, String>()
            override fun saveAuthToken(token: String) { prefs["auth_token"] = token }
            override fun getAuthToken(): String? = prefs["auth_token"]
            override fun clearAuthToken() { prefs.clear() }
        }
        
        val relayConfig = RelayConfig().apply {
            this.javaClass.getDeclaredField("baseUrl").apply { isAccessible = true }.set(this, relayBaseUrl)
            this.javaClass.getDeclaredField("wsUrl").apply { isAccessible = true }.set(this, relayWsUrl)
        }

        val httpClient = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
            }
            install(io.ktor.client.plugins.websocket.WebSockets) {
                contentConverter = io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
            }
        }

        val transport = NetworkMessageTransport(relayConfig, secureStorage, httpClient, identityKeyAlias, mockKeyStoreProvider)

        suspend fun setupIdentity(username: String) {
            val identity = crypto.generateIdentity(username)
            identityRepository.saveIdentity(identity.copy(id = ownStreamId))
            
            if (!commonKeyStore.containsAlias(identityKeyAlias)) {
                val kpg = KeyPairGenerator.getInstance("EC")
                kpg.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
                val kp = kpg.generateKeyPair()
                try {
                    commonKeyStore.setKeyEntry(identityKeyAlias, kp.private, null, null)
                } catch (e: Exception) { 
                    println("[6E-WARN] Failed to set key entry for $identityKeyAlias: $e")
                }
            }
        }

        fun cleanup() {
            database.close()
            httpClient.close()
            runBlocking { transport.disconnect() }
        }
    }
}