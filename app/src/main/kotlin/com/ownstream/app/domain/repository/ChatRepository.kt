package com.ownstream.app.domain.repository

import com.ownstream.app.domain.model.Conversation
import com.ownstream.app.domain.model.Message
import kotlinx.coroutines.flow.Flow

interface ChatRepository {
    fun getConversations(): Flow<List<Conversation>>
    fun getMessages(conversationId: String): Flow<List<Message>>
    
    suspend fun getConversation(conversationId: String): Conversation?
    suspend fun createConversation(conversation: Conversation)
    suspend fun sendMessage(message: Message)
    suspend fun deleteConversation(conversationId: String)
}
