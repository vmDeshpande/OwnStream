package com.ownstream.app.core.network

import com.ownstream.protocol.MessageEnvelope
import com.ownstream.protocol.ProtocolPreKeyBundle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flowOf
import java.util.concurrent.ConcurrentHashMap

/**
 * A deterministic in-memory transport and relay simulation for testing.
 * Multiple instances can share the same Relay state.
 */
class InMemoryMessageTransport(
    private val localIdentityId: String,
    private val relay: Relay = GlobalRelay
) : MessageTransport {

    /**
     * Shared state simulating the server side.
     */
    class Relay {
        private val preKeyBundles = ConcurrentHashMap<String, ProtocolPreKeyBundle>()
        private val messageBus = MutableSharedFlow<MessageEnvelope>(extraBufferCapacity = 100)
        private val offlineQueues = ConcurrentHashMap<String, MutableList<MessageEnvelope>>()

        fun publishBundle(identityId: String, bundle: ProtocolPreKeyBundle) {
            preKeyBundles[identityId] = bundle
        }

        fun fetchBundle(identityId: String): ProtocolPreKeyBundle? {
            return preKeyBundles[identityId]
        }

        suspend fun route(envelope: MessageEnvelope) {
            // Deliver to online subscribers
            messageBus.emit(envelope)
            
            // Also add to offline queue
            offlineQueues.computeIfAbsent(envelope.recipientId) { mutableListOf() }.add(envelope)
        }

        fun observeMessages() = messageBus

        fun fetchOfflineMessages(identityId: String): List<MessageEnvelope> {
            return offlineQueues.remove(identityId) ?: emptyList()
        }
    }

    companion object {
        val GlobalRelay = Relay()
    }

    private val incomingMessages = MutableSharedFlow<MessageEnvelope>(replay = 10)

    override suspend fun send(envelope: MessageEnvelope) {
        relay.route(envelope)
    }

    override fun observeIncomingMessages(): Flow<MessageEnvelope> = incomingMessages

    override fun observeConnectionStatus(): Flow<ConnectionStatus> = flowOf(ConnectionStatus.CONNECTED)

    override suspend fun publishPreKeyBundle(identityId: String, bundle: ProtocolPreKeyBundle) {
        relay.publishBundle(identityId, bundle)
    }

    override suspend fun fetchPreKeyBundle(identityId: String): ProtocolPreKeyBundle? {
        return relay.fetchBundle(identityId)
    }

    override suspend fun connect() {
        // Fetch offline messages on "connect"
        relay.fetchOfflineMessages(localIdentityId).forEach {
            incomingMessages.emit(it)
        }
    }

    suspend fun receiveFromBus(envelope: MessageEnvelope) {
        if (envelope.recipientId == localIdentityId) {
            incomingMessages.emit(envelope)
        }
    }

    override suspend fun disconnect() {}
}
