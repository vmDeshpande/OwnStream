package com.ownstream.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ownstream.app.core.crypto.*
import com.ownstream.app.core.network.*
import com.ownstream.app.core.storage.SecureStorage
import com.ownstream.protocol.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature

import android.util.Log
import androidx.room.Room
import com.ownstream.app.data.local.AppDatabase
import java.util.concurrent.TimeUnit
import org.junit.Rule
import org.junit.rules.Timeout

@RunWith(AndroidJUnit4::class)
class NetworkTransportIntegrationTest {

    @get:Rule
    val globalTimeout = Timeout(30, TimeUnit.SECONDS)

    private lateinit var transport: NetworkMessageTransport
    private lateinit var secureStorage: SecureStorage
    private val serverPort = 8888
    private val TAG = "NetworkTransportTest"
    private val KEY_ALIAS = "ownstream_identity_key"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        Log.d(TAG, "SETUP: Starting")
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        secureStorage = object : SecureStorage {
            private val prefs = mutableMapOf<String, String>()
            override fun saveAuthToken(token: String) { 
                Log.d(TAG, "MOCK_STORAGE: Saving token")
                prefs["auth_token"] = token 
            }
            override fun getAuthToken(): String? = prefs["auth_token"]
            override fun clearAuthToken() { prefs.clear() }
        }
        secureStorage.clearAuthToken()
        
        val mockKeyStoreProvider = object : KeyStoreProvider {
            override fun getKeyStore(): KeyStore {
                Log.d(TAG, "MOCK_KEYSTORE: Getting instance")
                return KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
            }
        }

        // Ensure identity key exists
        val ks = mockKeyStoreProvider.getKeyStore()
        if (!ks.containsAlias(KEY_ALIAS)) {
            Log.d(TAG, "SETUP: Generating identity key")
            val kpg = KeyPairGenerator.getInstance("EC", ANDROID_KEYSTORE)
            kpg.initialize(
                android.security.keystore.KeyGenParameterSpec.Builder(KEY_ALIAS, android.security.keystore.KeyProperties.PURPOSE_SIGN)
                    .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                    .build()
            )
            kpg.generateKeyPair()
            Log.d(TAG, "SETUP: Key generated")
        }

        val mockEngine = MockEngine { request ->
            val threadName = Thread.currentThread().name
            Log.d(TAG, "[$threadName] MOCK_ENGINE: Request received -> ${request.url}")
            when (request.url.encodedPath) {
                "/v1/register" -> {
                    Log.d(TAG, "[$threadName] MOCK_ENGINE: Handling /v1/register")
                    respond(
                        content = json.encodeToString(RegisterDeviceResponse(DeviceSessionInfo(1, 0, "os_test1234"))),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/v1/auth/challenge" -> {
                    Log.d(TAG, "[$threadName] MOCK_ENGINE: Handling /v1/auth/challenge")
                    respond(
                        content = json.encodeToString(AuthChallengeResponse(byteArrayOf(1, 2, 3))),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/v1/auth/login" -> {
                    Log.d(TAG, "[$threadName] MOCK_ENGINE: Handling /v1/auth/login")
                    respond(
                        content = json.encodeToString(LoginResponse("mock_token", System.currentTimeMillis() + 3600000, DeviceSessionInfo(1, 0, "os_test1234"))),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/v1/prekeys" -> {
                    Log.d(TAG, "[$threadName] MOCK_ENGINE: Handling /v1/prekeys")
                    if (request.headers[HttpHeaders.Authorization] == "Bearer mock_token") {
                        respond("", HttpStatusCode.OK)
                    } else {
                        respond("", HttpStatusCode.Unauthorized)
                    }
                }
                else -> {
                    Log.e(TAG, "[$threadName] MOCK_ENGINE: Unhandled path -> ${request.url.encodedPath}")
                    error("Unhandled path: ${request.url.encodedPath}")
                }
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val enc = EncryptionManager("test_storage", mockKeyStoreProvider)
        val signalStore = SignalProtocolStoreAdapter(db.signalDao(), enc)

        transport = NetworkMessageTransport(
            RelayConfig(context).apply { baseUrl = "http://localhost:$serverPort" },
            secureStorage,
            httpClient,
            KEY_ALIAS,
            mockKeyStoreProvider,
            signalStore
        )
        Log.d(TAG, "SETUP: Finished")
    }

    @Test
    fun testAuthenticationAndPublishPreKey() {
        runBlocking {
            val threadName = Thread.currentThread().name
            withTimeout(20000) {
                Log.d(TAG, "[$threadName] TEST: Starting testAuthenticationAndPublishPreKey with 20s timeout")
                val bundle = ProtocolPreKeyBundle(
                    registrationId = 11,
                    deviceId = 1,
                    preKeyId = 1,
                    preKeyPublicBase64 = "AAA=",
                    signedPreKeyId = 1,
                    signedPreKeyPublicBase64 = "BBB=",
                    signedPreKeySignatureBase64 = "CCC=",
                    identityKeyBase64 = "DDD=",
                    kyberPreKeyId = 1,
                    kyberPreKeyPublicBase64 = "EEE=",
                    kyberPreKeySignatureBase64 = "FFF="
                )

                Log.d(TAG, "[$threadName] TEST: Calling publishPreKeyBundle")
                // This will trigger registration, challenge, and login
                transport.publishPreKeyBundle("os_test1234", bundle)
                
                Log.d(TAG, "[$threadName] TEST: publishPreKeyBundle finished")
                assertNotNull(secureStorage.getAuthToken())
                assertEquals("mock_token", secureStorage.getAuthToken())
                Log.d(TAG, "[$threadName] TEST: Finished successfully")
            }
        }
    }
}
