package com.ownstream.app.core.storage

import com.ownstream.app.domain.model.Conversation
import com.ownstream.app.domain.model.Message

/**
 * Interface for exporting and importing conversation data between storage providers.
 */
interface MigrationService {
    /**
     * Exports a conversation and its messages into a versioned, portable data structure.
     */
    suspend fun exportConversation(conversationId: String): PortableConversationData

    /**
     * Imports a conversation from a portable data structure into the specified storage adapter.
     */
    suspend fun importConversation(data: PortableConversationData, targetAdapter: StorageAdapter)
}

/**
 * Portable, versioned data structure for OwnStream conversation migration.
 */
data class PortableConversationData(
    val version: Int,
    val conversation: Conversation,
    val messages: List<Message>,
    val exportTimestamp: Long,
    val metadata: Map<String, String> = emptyMap()
)
