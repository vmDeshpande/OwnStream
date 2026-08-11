package com.ownstream.relay

import io.ktor.server.websocket.*
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory registry for active WebSocket connections.
 */
class ConnectionRegistry {
    private val connections = ConcurrentHashMap<String, DefaultWebSocketServerSession>()

    fun addConnection(ownStreamId: String, session: DefaultWebSocketServerSession) {
        connections[ownStreamId] = session
    }

    fun removeConnection(ownStreamId: String) {
        connections.remove(ownStreamId)
    }

    fun getConnection(ownStreamId: String): DefaultWebSocketServerSession? {
        return connections[ownStreamId]
    }
}
