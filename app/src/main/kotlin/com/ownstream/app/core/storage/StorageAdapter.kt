package com.ownstream.app.core.storage

import com.ownstream.app.domain.model.Conversation
import com.ownstream.app.domain.model.Message
import com.ownstream.app.domain.model.StorageConfiguration
import kotlinx.coroutines.flow.Flow

/**
 * Core abstraction for message and conversation persistence.
 * This allows OwnStream to be storage-agnostic (Local Room, Remote Postgres, etc.)
 */
interface StorageAdapter {
    fun observeConversations(): Flow<List<Conversation>>
    fun observeMessages(conversationId: String): Flow<List<Message>>
    
    suspend fun getConversation(conversationId: String): Conversation?
    suspend fun saveConversation(conversation: Conversation)
    suspend fun deleteConversation(conversationId: String)
    
    suspend fun saveMessage(message: Message)
    suspend fun getMessage(messageId: String): Message?
    suspend fun deleteMessage(messageId: String)
    
    suspend fun getStorageConfiguration(conversationId: String): StorageConfiguration?
    suspend fun saveStorageConfiguration(config: StorageConfiguration)
}
