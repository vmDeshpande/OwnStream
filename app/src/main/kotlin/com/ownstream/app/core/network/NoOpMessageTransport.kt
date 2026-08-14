package com.ownstream.app.core.network

import com.ownstream.protocol.MessageEnvelope
import com.ownstream.protocol.ProtocolPreKeyBundle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/**
 * A fallback transport that does nothing.
 */
class NoOpMessageTransport @Inject constructor() : MessageTransport {
    override suspend fun send(envelope: MessageEnvelope) {}
    override fun observeIncomingMessages(): Flow<MessageEnvelope> = emptyFlow()
    override fun observeConnectionStatus(): Flow<ConnectionStatus> = flowOf(ConnectionStatus.DISCONNECTED)
    override suspend fun publishPreKeyBundle(identityId: String, bundle: ProtocolPreKeyBundle) {}
    override suspend fun fetchPreKeyBundle(identityId: String): ProtocolPreKeyBundle? = null
    override suspend fun uploadMedia(request: com.ownstream.protocol.UploadMediaRequest): String = ""
    override suspend fun downloadMedia(fileId: String): com.ownstream.protocol.DownloadMediaResponse = com.ownstream.protocol.DownloadMediaResponse("", "")
    override suspend fun connect() {}
    override suspend fun disconnect() {}
}
