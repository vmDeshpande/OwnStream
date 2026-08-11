package com.ownstream.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ownstream.app.core.network.NetworkMessageTransport
import com.ownstream.app.core.network.RelayConfig
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

@RunWith(AndroidJUnit4::class)
class NetworkTransportIntegrationTest {

    private lateinit var transport: NetworkMessageTransport
    private lateinit var secureStorage: SecureStorage
    private val KEY_ALIAS = "ownstream_identity_key"
    private val ANDROID_KEYSTORE = "AndroidKeyStore"
    private val json = Json { ignoreUnknownKeys = true }

    @Before
    fun setup() {
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        secureStorage = SecureStorage(context)
        secureStorage.clearAuthToken()
        
        // Ensure identity key exists
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        if (!ks.containsAlias(KEY_ALIAS)) {
            val kpg = KeyPairGenerator.getInstance("EC", ANDROID_KEYSTORE)
            kpg.initialize(
                android.security.keystore.KeyGenParameterSpec.Builder(KEY_ALIAS, android.security.keystore.KeyProperties.PURPOSE_SIGN)
                    .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256)
                    .build()
            )
            kpg.generateKeyPair()
        }

        val mockEngine = MockEngine { request ->
            when (request.url.encodedPath) {
                "/v1/register" -> {
                    respond(
                        content = json.encodeToString(RegisterDeviceResponse(DeviceSessionInfo(1, 0, "os_test123"))),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/v1/auth/challenge" -> {
                    respond(
                        content = json.encodeToString(AuthChallengeResponse(byteArrayOf(1, 2, 3))),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/v1/auth/login" -> {
                    respond(
                        content = json.encodeToString(LoginResponse("mock_token", System.currentTimeMillis() + 3600000, DeviceSessionInfo(1, 0, "os_test123"))),
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json")
                    )
                }
                "/v1/prekeys" -> {
                    if (request.headers[HttpHeaders.Authorization] == "Bearer mock_token") {
                        respond("", HttpStatusCode.OK)
                    } else {
                        respond("", HttpStatusCode.Unauthorized)
                    }
                }
                else -> error("Unhandled path: ${request.url.encodedPath}")
            }
        }

        val httpClient = HttpClient(mockEngine) {
            install(ContentNegotiation) { json(json) }
        }

        transport = NetworkMessageTransport(
            RelayConfig(),
            secureStorage,
            httpClient
        )
    }

    @Test
    fun testAuthenticationAndPublishPreKey() = runBlocking {
        val bundle = ProtocolPreKeyBundle(
            registrationId = 11,
            deviceId = 1,
            preKeyId = 1,
            preKeyPublic = byteArrayOf(1),
            signedPreKeyId = 1,
            signedPreKeyPublic = byteArrayOf(1),
            signedPreKeySignature = byteArrayOf(1),
            identityKey = byteArrayOf(1),
            kyberPreKeyId = 1,
            kyberPreKeyPublic = byteArrayOf(1),
            kyberPreKeySignature = byteArrayOf(1)
        )

        // This will trigger registration, challenge, and login
        transport.publishPreKeyBundle("os_test123", bundle)
        
        assertNotNull(secureStorage.getAuthToken())
        assertEquals("mock_token", secureStorage.getAuthToken())
    }
}
