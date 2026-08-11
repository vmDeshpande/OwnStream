package com.ownstream.relay

import com.ownstream.protocol.*
import com.ownstream.protocol.FrameType as ProtocolFrameType
import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.plugins.websocket.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.junit.Assert.*
import org.junit.Test
import java.security.*
import java.security.spec.ECGenParameterSpec

class RelayTest {

    private fun generateECKeyPair(): KeyPair {
        val keyPairGenerator = KeyPairGenerator.getInstance("EC")
        keyPairGenerator.initialize(ECGenParameterSpec("secp256r1"))
        return keyPairGenerator.generateKeyPair()
    }

    private fun signData(privateKey: PrivateKey, data: ByteArray): ByteArray {
        val sig = Signature.getInstance("SHA256withECDSA")
        sig.initSign(privateKey)
        sig.update(data)
        return sig.sign()
    }

    private suspend fun registerAndLogin(client: io.ktor.client.HttpClient, ownStreamId: String): String {
        val keys = generateECKeyPair()
        
        val regResponse = client.post("/v1/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterDeviceRequest(ownStreamId, keys.public.encoded))
        }
        assertEquals(HttpStatusCode.OK, regResponse.status)
        
        val challenge = client.get("/v1/auth/challenge?ownStreamId=$ownStreamId").body<AuthChallengeResponse>()
        val signature = signData(keys.private, challenge.nonce)
        
        return client.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(ownStreamId, challenge.nonce, signature))
        }.body<LoginResponse>().token
    }

    @Test
    fun testRegistrationAndAuthFlow() = testApplication {
        application { module() }
        val client = createClient {
            install(ClientContentNegotiation) { json() }
        }

        val aliceId = "os_a1b2c3d4"
        val aliceToken = registerAndLogin(client, aliceId)
        assertTrue(aliceToken.startsWith("mockjwt:"))

        // 4. Publish PreKey Bundle
        val bundle = ProtocolPreKeyBundle(
            registrationId = 123,
            deviceId = 1,
            preKeyId = 1,
            preKeyPublic = byteArrayOf(1, 2, 3),
            signedPreKeyId = 1,
            signedPreKeyPublic = byteArrayOf(4, 5, 6),
            signedPreKeySignature = byteArrayOf(7, 8, 9),
            identityKey = byteArrayOf(10, 11, 12),
            kyberPreKeyId = 1,
            kyberPreKeyPublic = byteArrayOf(13, 14, 15),
            kyberPreKeySignature = byteArrayOf(16, 17, 18)
        )

        val publishResponse = client.post("/v1/prekeys") {
            header(HttpHeaders.Authorization, "Bearer $aliceToken")
            contentType(ContentType.Application.Json)
            setBody(PublishPreKeyBundleRequest(bundle))
        }
        assertEquals(HttpStatusCode.OK, publishResponse.status)

        // 5. Fetch PreKey Bundle
        val fetchResponse = client.get("/v1/prekeys/$aliceId") {
            header(HttpHeaders.Authorization, "Bearer $aliceToken")
        }
        assertEquals(HttpStatusCode.OK, fetchResponse.status)
        val fetchedData = fetchResponse.body<FetchPreKeyBundleResponse>()
        assertEquals(bundle.registrationId, fetchedData.bundle.registrationId)
        assertArrayEquals(bundle.preKeyPublic, fetchedData.bundle.preKeyPublic)

        // 6. Consume One-Time PreKey
        val fetchResponse2 = client.get("/v1/prekeys/$aliceId") {
            header(HttpHeaders.Authorization, "Bearer $aliceToken")
        }
        assertEquals(HttpStatusCode.OK, fetchResponse2.status)
        val fetchedData2 = fetchResponse2.body<FetchPreKeyBundleResponse>()
        assertNull(fetchedData2.bundle.preKeyPublic)
    }

    @Test
    fun testOnlineRouting() = testApplication {
        application { module() }
        val httpClient = createClient { install(ClientContentNegotiation) { json() } }
        val aliceToken = registerAndLogin(httpClient, "os_a1b2c3d4")
        val bobToken = registerAndLogin(httpClient, "os_b2c3d4e5")

        val client = createClient {
            install(WebSockets) {
                contentConverter = KotlinxWebsocketSerializationConverter(Json)
            }
        }

        // Bob connects
        client.webSocket("/v1/ws", { header(HttpHeaders.Authorization, "Bearer $bobToken") }) {
            val aliceClient = createClient {
                install(WebSockets) { contentConverter = KotlinxWebsocketSerializationConverter(Json) }
            }
            
            // Alice connects and sends to Bob
            aliceClient.webSocket("/v1/ws", { header(HttpHeaders.Authorization, "Bearer $aliceToken") }) {
                val envelope = MessageEnvelope(
                    messageId = "msg_online",
                    conversationId = "c1",
                    senderId = "os_a1b2c3d4",
                    recipientId = "os_b2c3d4e5",
                    timestamp = System.currentTimeMillis(),
                    encryptedPayload = EncryptedPayload(byteArrayOf(1, 2, 3), "SIGNAL_V1", true)
                )
                val frame = WebSocketFrame(type = ProtocolFrameType.ENVELOPE, payload = FramePayload.Envelope(envelope))
                sendSerialized(frame)
                
                // Alice gets RELAY_RECEIVED
                val aliceAck = receiveDeserialized<WebSocketFrame>()
                assertEquals(ProtocolFrameType.DELIVERY_ACK, aliceAck.type)
            }

            // Bob receives
            val bobReceived = receiveDeserialized<WebSocketFrame>()
            assertEquals(ProtocolFrameType.ENVELOPE, bobReceived.type)
        }
    }

    @Test
    fun testOfflineQueuing() = testApplication {
        application { module() }
        val httpClient = createClient { install(ClientContentNegotiation) { json() } }
        
        val aliceKeys = generateECKeyPair()
        val aliceId = "os_c3d4e5f6"
        httpClient.post("/v1/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterDeviceRequest(aliceId, aliceKeys.public.encoded))
        }
        val aliceChallenge = httpClient.get("/v1/auth/challenge?ownStreamId=$aliceId").body<AuthChallengeResponse>()
        val aliceToken = httpClient.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(aliceId, aliceChallenge.nonce, signData(aliceKeys.private, aliceChallenge.nonce)))
        }.body<LoginResponse>().token

        val bobKeys = generateECKeyPair()
        val bobId = "os_d4e5f6a1"
        httpClient.post("/v1/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterDeviceRequest(bobId, bobKeys.public.encoded))
        }

        val client = createClient {
            install(WebSockets) { contentConverter = KotlinxWebsocketSerializationConverter(Json) }
        }

        // Alice sends while Bob is offline
        client.webSocket("/v1/ws", { header(HttpHeaders.Authorization, "Bearer $aliceToken") }) {
            val envelope = MessageEnvelope(
                messageId = "msg_offline",
                conversationId = "c1",
                senderId = aliceId,
                recipientId = bobId,
                timestamp = System.currentTimeMillis(),
                encryptedPayload = EncryptedPayload(byteArrayOf(4, 5, 6), "SIGNAL_V1", true)
            )
            sendSerialized(WebSocketFrame(type = ProtocolFrameType.ENVELOPE, payload = FramePayload.Envelope(envelope)))
            receiveDeserialized<WebSocketFrame>() // ACK
        }

        // Bob connects later
        val bobChallenge = httpClient.get("/v1/auth/challenge?ownStreamId=$bobId").body<AuthChallengeResponse>()
        val bobToken = httpClient.post("/v1/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(bobId, bobChallenge.nonce, signData(bobKeys.private, bobChallenge.nonce)))
        }.body<LoginResponse>().token

        client.webSocket("/v1/ws", { header(HttpHeaders.Authorization, "Bearer $bobToken") }) {
            val queued = receiveDeserialized<WebSocketFrame>()
            assertEquals("msg_offline", (queued.payload as FramePayload.Envelope).envelope.messageId)
            
            // ACK it
            sendSerialized(WebSocketFrame(type = ProtocolFrameType.DELIVERY_ACK, payload = FramePayload.DeliveryAck("msg_offline", AckStatus.DELIVERED_TO_RECIPIENT)))
        }
    }

    @Test
    fun testRelayBlindness() = testApplication {
        application { module() }
        val httpClient = createClient { install(ClientContentNegotiation) { json() } }
        val aliceId = "os_12345678"
        val aliceToken = registerAndLogin(httpClient, aliceId)
        
        val bobId = "os_87654321"
        registerAndLogin(httpClient, bobId)
        
        val client = createClient {
            install(WebSockets) { contentConverter = KotlinxWebsocketSerializationConverter(Json) }
        }

        val plaintext = "Secret Message"
        val envelope = MessageEnvelope(
            messageId = "blind_msg",
            conversationId = "c1",
            senderId = aliceId,
            recipientId = bobId,
            timestamp = System.currentTimeMillis(),
            encryptedPayload = EncryptedPayload(plaintext.toByteArray(), "SIGNAL_V1", true)
        )

        client.webSocket("/v1/ws", { header(HttpHeaders.Authorization, "Bearer $aliceToken") }) {
            sendSerialized(WebSocketFrame(type = ProtocolFrameType.ENVELOPE, payload = FramePayload.Envelope(envelope)))
            receiveDeserialized<WebSocketFrame>()
        }

        // Verify database directly
        val messages = org.jetbrains.exposed.sql.transactions.transaction {
            QueuedMessages.selectAll().where { QueuedMessages.messageId eq "blind_msg" }.map {
                it[QueuedMessages.envelopeData]
            }
        }
        assertEquals(1, messages.size)
        val storedData = String(messages[0])
        assertFalse("Relay database should not contain plaintext", storedData.contains(plaintext))
    }

    @Test
    fun testInvalidOwnStreamId() = testApplication {
        application { module() }
        val client = createClient { install(ClientContentNegotiation) { json() } }
        
        val response = client.post("/v1/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterDeviceRequest("not_a_valid_id", byteArrayOf()))
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
