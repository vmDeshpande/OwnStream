package com.ownstream.protocol

import kotlinx.serialization.Serializable

/**
 * Top-level frame for WebSocket communication.
 */
@Serializable
data class WebSocketFrame(
    val protocolVersion: Int = OwnStreamProtocol.VERSION_1,
    val type: FrameType,
    val requestId: String? = null,
    val payload: FramePayload
)

@Serializable
enum class FrameType {
    ENVELOPE,
    DELIVERY_ACK,
    HEARTBEAT,
    HISTORY_SYNC,
    HISTORY_SYNC_COMPLETE,
    ERROR
}

@Serializable
sealed class FramePayload {
    /**
     * CLIENT -> SERVER: Send message
     * SERVER -> CLIENT: Deliver message
     */
    @Serializable
    data class Envelope(val envelope: MessageEnvelope) : FramePayload()

    /**
     * CLIENT -> SERVER: Acknowledge receipt of a message (removes from relay queue)
     * SERVER -> CLIENT: Acknowledge server-side receipt
     */
    @Serializable
    data class DeliveryAck(
        val messageId: String,
        val status: AckStatus
    ) : FramePayload()

    /**
     * CLIENT <-> SERVER: Keep-alive
     */
    @Serializable
    data object Heartbeat : FramePayload()

    /**
     * CLIENT -> SERVER: Ask for missed messages since timestamp
     */
    @Serializable
    data class HistorySyncRequest(val sinceTimestamp: Long) : FramePayload()

    /**
     * SERVER -> CLIENT: Confirming sync end
     */
    @Serializable
    data object HistorySyncComplete : FramePayload()

    /**
     * SERVER -> CLIENT: Error message
     */
    @Serializable
    data class Error(val code: String, val message: String) : FramePayload()
}

@Serializable
enum class AckStatus {
    RECEIVED_BY_RELAY,
    DELIVERED_TO_RECIPIENT,
    DUPLICATE_MESSAGE,
    REJECTED_MESSAGE
}
