package com.ownstream.app.core.network

import com.ownstream.protocol.MessageEnvelope
import com.ownstream.protocol.ProtocolPreKeyBundle
import kotlinx.coroutines.flow.Flow

/**
 * Core abstraction for sending and receiving messages.
 * Transport is independent of storage and cryptography.
 */
interface MessageTransport {
    /**
     * Sends an encrypted message packet.
     * In 1:1 E2EE, this envelope contains the routing information for the relay.
     */
    suspend fun send(envelope: MessageEnvelope)

    /**
     * Observes incoming message packets.
     */
    fun observeIncomingMessages(): Flow<MessageEnvelope>

    /**
     * Publishes the local PreKey bundle to the relay for discovery.
     */
    suspend fun publishPreKeyBundle(identityId: String, bundle: ProtocolPreKeyBundle)

    /**
     * Fetches a remote identity's PreKey bundle from the relay.
     */
    suspend fun fetchPreKeyBundle(identityId: String): ProtocolPreKeyBundle?

    /**
     * Uploads encrypted media to the relay.
     */
    suspend fun uploadMedia(request: com.ownstream.protocol.UploadMediaRequest): String

    /**
     * Downloads encrypted media from the relay.
     */
    suspend fun downloadMedia(fileId: String): com.ownstream.protocol.DownloadMediaResponse


    /**
     * Observes the current connection status of the transport.
     */
    fun observeConnectionStatus(): Flow<ConnectionStatus>

    /**
     * Connects to the transport layer (e.g., WebSocket, Relay, Mesh).
     */
    suspend fun connect()

    /**
     * Disconnects from the transport layer.
     */
    suspend fun disconnect()
}

enum class ConnectionStatus {
    DISCONNECTED, CONNECTING, CONNECTED, ERROR
}

