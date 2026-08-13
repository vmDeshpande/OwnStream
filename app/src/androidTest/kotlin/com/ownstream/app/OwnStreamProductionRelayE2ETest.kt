package com.ownstream.app

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ownstream.app.core.crypto.*
import com.ownstream.app.core.network.*
import com.ownstream.app.core.storage.SecureStorage
import com.ownstream.app.data.local.AppDatabase
import com.ownstream.app.data.repository.RealIdentityRepository
import com.ownstream.protocol.*
import com.ownstream.protocol.FrameType as ProtocolFrameType
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
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds
import org.junit.Rule
import org.junit.rules.Timeout

@RunWith(AndroidJUnit4::class)
class OwnStreamProductionRelayE2ETest {

    @get:Rule
    val globalTimeout = Timeout(3, TimeUnit.MINUTES)

    private val TAG = "E2E"
    private lateinit var relayServer: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>
    private val relayPort = 9091
    private val relayBaseUrl = "http://127.0.0.1:$relayPort"
    private val relayWsUrl = "ws://127.0.0.1:$relayPort/v1/ws"

    private lateinit var aliceEnv: TestDeviceEnvironment
    private lateinit var bobEnv: TestDeviceEnvironment

    @Before
    fun setup() {
        Log.i(TAG, "[E2E] @Before START")
        
        // Start relay server in background
        Log.i(TAG, "[E2E] Launching Relay on 0.0.0.0:$relayPort")
        relayServer = embeddedServer(CIO, port = relayPort, host = "0.0.0.0") {
            module()
        }
        relayServer.start(wait = false)
        Log.i(TAG, "[E2E] Relay start() returned")

        // Wait for server manually
        Log.i(TAG, "[E2E] Sleeping 5s for Relay...")
        Thread.sleep(5000)
        Log.i(TAG, "[E2E] Sleep finished")

        val context = ApplicationProvider.getApplicationContext<Context>()

        Log.i(TAG, "[E2E] Initializing Alice Env")
        aliceEnv = TestDeviceEnvironment(context, "os_a1b2c3d4", "alice_storage_key", "alice_identity_key", relayBaseUrl, relayWsUrl)
        Log.i(TAG, "[E2E] Alice Env Ready")
        
        Log.i(TAG, "[E2E] Initializing Bob Env")
        bobEnv = TestDeviceEnvironment(context, "os_e5f6a7b8", "bob_storage_key", "bob_identity_key", relayBaseUrl, relayWsUrl)
        Log.i(TAG, "[E2E] Bob Env Ready")
        
        Log.i(TAG, "[E2E] @Before FINISHED")
    }

    @After
    fun tearDown() {
        Log.i(TAG, "[E2E] @After START")
        if (::aliceEnv.isInitialized) aliceEnv.cleanup()
        if (::bobEnv.isInitialized) bobEnv.cleanup()
        if (::relayServer.isInitialized) {
            Log.i(TAG, "[E2E] Stopping Relay Server")
            relayServer.stop(1000, 2000)
        }
        Log.i(TAG, "[E2E] @After FINISHED")
    }

    @Test
    fun testAliceAndBobFullE2E() {
        Log.i(TAG, "[E2E] TEST START")
        runBlocking {
            withTimeout(150000) {
                // A. Setup Identities and Publish PreKeys
                Log.i(TAG, "[E2E] ALICE_IDENTITY_SETUP")
                aliceEnv.setupIdentity("Alice")
                
                Log.i(TAG, "[E2E] BOB_IDENTITY_SETUP")
                bobEnv.setupIdentity("Bob")

                Log.i(TAG, "[E2E] ALICE_BUNDLE_PUBLISH")
                val aliceBundle = aliceEnv.crypto.getLocalPreKeyBundle()
                aliceEnv.transport.publishPreKeyBundle(aliceEnv.ownStreamId, aliceBundle)
                
                Log.i(TAG, "[E2E] BOB_BUNDLE_PUBLISH")
                val bobBundleLocal = bobEnv.crypto.getLocalPreKeyBundle()
                bobEnv.transport.publishPreKeyBundle(bobEnv.ownStreamId, bobBundleLocal)

                // B. Connect to WebSockets
                Log.i(TAG, "[E2E] WS_CONNECT_ALL")
                val aliceJob = launch { 
                    Log.i(TAG, "[E2E] ALICE_WS_CONNECTING")
                    aliceEnv.transport.connect(aliceEnv.ownStreamId) 
                }
                val bobJob = launch { 
                    Log.i(TAG, "[E2E] BOB_WS_CONNECTING")
                    bobEnv.transport.connect(bobEnv.ownStreamId) 
                }
                Log.i(TAG, "[E2E] WS_STABILIZE_WAIT")
                delay(5000) 

                // C. Alice establishes session with Bob
                Log.i(TAG, "[E2E] ALICE_FETCH_BOB_BUNDLE")
                val bobBundleFetched = aliceEnv.transport.fetchPreKeyBundle(bobEnv.ownStreamId)
                assertNotNull("Alice should be able to fetch Bob's bundle", bobBundleFetched)
                Log.i(TAG, "[E2E] ALICE_ESTABLISH_SESSION")
                aliceEnv.crypto.establishSession(bobEnv.ownStreamId, bobBundleFetched!!)
                Log.i(TAG, "[E2E] ALICE_SESSION_READY")

                // D. Alice sends message to Bob
                val aliceToBobText = "Hello from Alice through the real relay!"
                Log.i(TAG, "[E2E] ALICE_SEND_MESSAGE")
                val aliceEncrypted = aliceEnv.crypto.encryptPayload(aliceToBobText, listOf(bobEnv.ownStreamId))
                val aliceEnvelope = MessageEnvelope(
                    messageId = "msg_a_to_b_" + UUID.randomUUID().toString().take(6),
                    conversationId = "conv_shared",
                    senderId = aliceEnv.ownStreamId,
                    recipientId = bobEnv.ownStreamId,
                    timestamp = System.currentTimeMillis(),
                    encryptedPayload = aliceEncrypted
                )

                aliceEnv.transport.send(aliceEnvelope)
                Log.i(TAG, "[E2E] ALICE_SENT_ENVELOPE")

                // E. Bob receives and decrypts
                Log.i(TAG, "[E2E] BOB_RECEIVE_WAIT")
                val bobReceived = withTimeout(30000) {
                    bobEnv.transport.observeIncomingMessages().first()
                }
                Log.i(TAG, "[E2E] BOB_RECEIVED")
                assertEquals(aliceEnvelope.messageId, bobReceived.messageId)
                
                Log.i(TAG, "[E2E] BOB_DECRYPT")
                val bobDecrypted = bobEnv.crypto.decryptPayload(bobReceived.encryptedPayload, aliceEnv.ownStreamId)
                assertEquals(aliceToBobText, bobDecrypted)

                // F. Bob replies to Alice
                val bobToAliceText = "Hi Alice! Received your message. E2EE works!"
                Log.i(TAG, "[E2E] BOB_REPLY_SEND")
                
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

                // G. Alice receives and decrypts reply
                Log.i(TAG, "[E2E] ALICE_REPLY_RECEIVE_WAIT")
                val aliceReceived = withTimeout(30000) {
                    aliceEnv.transport.observeIncomingMessages().first()
                }
                Log.i(TAG, "[E2E] ALICE_REPLY_RECEIVED")
                assertEquals(bobEnvelope.messageId, aliceReceived.messageId)
                
                Log.i(TAG, "[E2E] ALICE_REPLY_DECRYPT")
                val aliceDecrypted = aliceEnv.crypto.decryptPayload(aliceReceived.encryptedPayload, bobEnv.ownStreamId)
                assertEquals(bobToAliceText, aliceDecrypted)

                aliceJob.cancel()
                bobJob.cancel()
                Log.i(TAG, "[E2E] SUCCESS_FINAL")
            }
        }
        Log.i(TAG, "[E2E] TEST FINISHED")
    }

    class TestDeviceEnvironment(
        val context: Context,
        val ownStreamId: String,
        val storageKeyAlias: String,
        val identityKeyAlias: String,
        val relayBaseUrl: String,
        val relayWsUrl: String
    ) {
        val mockKeyStoreProvider = object : KeyStoreProvider {
            override fun getKeyStore(): KeyStore {
                return KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            }
        }

        val database: AppDatabase = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        val encryptionManager = EncryptionManager(storageKeyAlias, mockKeyStoreProvider)
        val storeAdapter = SignalProtocolStoreAdapter(database.signalDao(), encryptionManager)
        
        private val identityDao = database.identityDao()
        val identityRepository = RealIdentityRepository(identityDao)
        
        val crypto = SignalCryptoProvider(storeAdapter, identityRepository, identityKeyAlias, mockKeyStoreProvider)
        
        val secureStorage = object : SecureStorage {
            private val prefs = mutableMapOf<String, String>()
            override fun saveAuthToken(token: String) { prefs["auth_token"] = token }
            override fun getAuthToken(): String? = prefs["auth_token"]
            override fun clearAuthToken() { prefs.clear() }
        }
        
        val relayConfig = RelayConfig(context).apply {
            baseUrl = relayBaseUrl
        }

        val httpClient = HttpClient(io.ktor.client.engine.okhttp.OkHttp) {
            install(io.ktor.client.plugins.contentnegotiation.ContentNegotiation) {
                json(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
            }
            install(io.ktor.client.plugins.websocket.WebSockets) {
                contentConverter = io.ktor.serialization.kotlinx.KotlinxWebsocketSerializationConverter(kotlinx.serialization.json.Json { ignoreUnknownKeys = true })
            }
        }

        val transport = NetworkMessageTransport(relayConfig, secureStorage, httpClient, identityKeyAlias, mockKeyStoreProvider, storeAdapter)

        suspend fun setupIdentity(username: String) {
            Log.i("E2E", "[Device-$ownStreamId] Generating identity...")
            val identity = crypto.generateIdentity(username)
            identityRepository.saveIdentity(identity.copy(id = ownStreamId))
            
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            if (!ks.containsAlias(identityKeyAlias)) {
                Log.i("E2E", "[Device-$ownStreamId] Creating Keystore identity key...")
                val kpg = KeyPairGenerator.getInstance("EC", "AndroidKeyStore")
                kpg.initialize(
                    android.security.keystore.KeyGenParameterSpec.Builder(identityKeyAlias, android.security.keystore.KeyProperties.PURPOSE_SIGN)
                        .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                        .build()
                )
                kpg.generateKeyPair()
                Log.i("E2E", "[Device-$ownStreamId] Keystore key created.")
            }
        }

        fun cleanup() {
            runBlocking {
                database.close()
                httpClient.close()
                transport.disconnect()
            }
        }
    }
}
