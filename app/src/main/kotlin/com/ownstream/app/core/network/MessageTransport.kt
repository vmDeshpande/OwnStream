package com.ownstream.app.core.network

import com.ownstream.app.domain.model.Message
import kotlinx.coroutines.flow.Flow

/**
 * Core abstraction for sending and receiving messages.
 * Transport is independent of storage and cryptography.
 */
interface MessageTransport {
    /**
     * Sends an encrypted message packet.
     */
    suspend fun send(message: Message)

    /**
     * Observes incoming message packets.
     */
    fun observeIncomingMessages(): Flow<Message>

    /**
     * Connects to the transport layer (e.g., WebSocket, Relay, Mesh).
     */
    suspend fun connect()

    /**
     * Disconnects from the transport layer.
     */
    suspend fun disconnect()
}
