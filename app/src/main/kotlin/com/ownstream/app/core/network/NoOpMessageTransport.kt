package com.ownstream.app.core.network

import com.ownstream.protocol.MessageEnvelope
import com.ownstream.protocol.ProtocolPreKeyBundle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

/**
 * Placeholder transport for the local-only MVP.
 */
class NoOpMessageTransport @Inject constructor() : MessageTransport {
    override suspend fun send(envelope: MessageEnvelope) {
        // Do nothing in local MVP
    }

    override fun observeIncomingMessages(): Flow<MessageEnvelope> {
        return emptyFlow()
    }

    override suspend fun publishPreKeyBundle(identityId: String, bundle: ProtocolPreKeyBundle) {
        // No-op
    }

    override suspend fun fetchPreKeyBundle(identityId: String): ProtocolPreKeyBundle? {
        return null
    }

    override suspend fun connect() {}

    override suspend fun disconnect() {}
}
