package com.ownstream.app.core.network

import com.ownstream.app.domain.model.Message
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject

/**
 * Placeholder transport for the local-only MVP.
 */
class NoOpMessageTransport @Inject constructor() : MessageTransport {
    override suspend fun send(message: Message) {
        // Do nothing in local MVP
    }

    override fun observeIncomingMessages(): Flow<Message> {
        return emptyFlow()
    }

    override suspend fun connect() {}

    override suspend fun disconnect() {}
}
