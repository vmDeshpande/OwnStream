package com.ownstream.protocol

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ProtocolSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testMessageEnvelopeSerialization() {
        val payload = EncryptedPayload(
            data = byteArrayOf(1, 2, 3, 4),
            algorithm = "SIGNAL_V1",
            isEncrypted = true,
            metadata = mapOf("type" to "3")
        )
        val envelope = MessageEnvelope(
            messageId = "msg_123",
            conversationId = "conv_456",
            senderId = "os_alice",
            recipientId = "os_bob",
            timestamp = 1690000000000L,
            encryptedPayload = payload
        )

        val serialized = json.encodeToString(envelope)
        val deserialized = json.decodeFromString<MessageEnvelope>(serialized)

        assertEquals(envelope.messageId, deserialized.messageId)
        assertEquals(envelope.senderId, deserialized.senderId)
        assertArrayEquals(envelope.encryptedPayload.data, deserialized.encryptedPayload.data)
        assertEquals(envelope.encryptedPayload.algorithm, deserialized.encryptedPayload.algorithm)
    }

    @Test
    fun testWebSocketFrameEnvelope() {
        val payload = EncryptedPayload(byteArrayOf(5, 6, 7, 8), "SIGNAL_V1", true)
        val envelope = MessageEnvelope("id", "c1", "s1", "r1", 0L, payload)
        val frame = WebSocketFrame(
            type = FrameType.ENVELOPE,
            requestId = "req_1",
            payload = FramePayload.Envelope(envelope)
        )

        val serialized = json.encodeToString(frame)
        val deserialized = json.decodeFromString<WebSocketFrame>(serialized)

        assertEquals(FrameType.ENVELOPE, deserialized.type)
        assertTrue(deserialized.payload is FramePayload.Envelope)
        val innerEnvelope = (deserialized.payload as FramePayload.Envelope).envelope
        assertArrayEquals(envelope.encryptedPayload.data, innerEnvelope.encryptedPayload.data)
    }

    @Test
    fun testWebSocketFrameAck() {
        val frame = WebSocketFrame(
            type = FrameType.DELIVERY_ACK,
            payload = FramePayload.DeliveryAck("msg_1", AckStatus.DELIVERED_TO_RECIPIENT)
        )

        val serialized = json.encodeToString(frame)
        val deserialized = json.decodeFromString<WebSocketFrame>(serialized)

        assertEquals(FrameType.DELIVERY_ACK, deserialized.type)
        val ack = deserialized.payload as FramePayload.DeliveryAck
        assertEquals("msg_1", ack.messageId)
        assertEquals(AckStatus.DELIVERED_TO_RECIPIENT, ack.status)
    }

    @Test
    fun testPreKeyBundleSerialization() {
        val bundle = ProtocolPreKeyBundle(
            registrationId = 11,
            deviceId = 1,
            preKeyId = 22,
            preKeyPublic = byteArrayOf(10, 20),
            signedPreKeyId = 33,
            signedPreKeyPublic = byteArrayOf(30, 40),
            signedPreKeySignature = byteArrayOf(50),
            identityKey = byteArrayOf(60),
            kyberPreKeyId = 44,
            kyberPreKeyPublic = byteArrayOf(70, 80),
            kyberPreKeySignature = byteArrayOf(90)
        )

        val serialized = json.encodeToString(bundle)
        val deserialized = json.decodeFromString<ProtocolPreKeyBundle>(serialized)

        assertEquals(bundle.registrationId, deserialized.registrationId)
        assertArrayEquals(bundle.kyberPreKeyPublic, deserialized.kyberPreKeyPublic)
    }

    @Test
    fun testAuthChallengeRoundTrip() {
        val challenge = AuthChallengeResponse(byteArrayOf(1, 2, 3))
        val serialized = json.encodeToString(challenge)
        val deserialized = json.decodeFromString<AuthChallengeResponse>(serialized)
        assertArrayEquals(challenge.nonce, deserialized.nonce)
    }

    @Test
    fun testLoginRequestSerialization() {
        val request = LoginRequest("os_1", byteArrayOf(1), byteArrayOf(2))
        val serialized = json.encodeToString(request)
        val deserialized = json.decodeFromString<LoginRequest>(serialized)
        assertEquals(request.ownStreamId, deserialized.ownStreamId)
        assertArrayEquals(request.signature, deserialized.signature)
    }

    @Test
    fun testProtocolVersionHandling() {
        val frame = WebSocketFrame(
            protocolVersion = 1,
            type = FrameType.HEARTBEAT,
            payload = FramePayload.Heartbeat
        )
        val serialized = json.encodeToString(frame)
        val deserialized = json.decodeFromString<WebSocketFrame>(serialized)
        assertEquals(1, deserialized.protocolVersion)
    }

    @Test
    fun testValidation() {
        assertTrue(ProtocolValidation.isValidOwnStreamId("os_5af40408"))
        assertFalse(ProtocolValidation.isValidOwnStreamId("not_an_id"))
        assertFalse(ProtocolValidation.isValidOwnStreamId("os_too_long_123"))
        
        assertTrue(ProtocolValidation.isPayloadSizeValid(100))
        assertFalse(ProtocolValidation.isPayloadSizeValid(OwnStreamProtocol.MAX_MESSAGE_SIZE_BYTES + 1))
    }

    @Test(expected = Exception::class)
    fun testMalformedPayloadRejection() {
        // Purposely malformed JSON for MessageEnvelope
        val malformedJson = """{"messageId": "1", "encryptedPayload": { "data": [1,2], "algorithm": "NONE" }}"""
        json.decodeFromString<MessageEnvelope>(malformedJson)
    }
}
