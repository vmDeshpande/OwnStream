package com.ownstream.app.data.repository

import com.ownstream.app.core.storage.StorageAdapter
import com.ownstream.app.domain.model.Conversation
import com.ownstream.app.domain.model.Message
import com.ownstream.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RealChatRepository @Inject constructor(
    private val storageAdapter: StorageAdapter
) : ChatRepository {

    override fun getConversations(): Flow<List<Conversation>> {
        return storageAdapter.observeConversations()
    }

    override fun getMessages(conversationId: String): Flow<List<Message>> {
        return storageAdapter.observeMessages(conversationId)
    }

    override suspend fun getConversation(conversationId: String): Conversation? {
        return storageAdapter.getConversation(conversationId)
    }

    override suspend fun createConversation(conversation: Conversation) {
        storageAdapter.saveConversation(conversation)
    }

    override suspend fun sendMessage(message: Message) {
        storageAdapter.saveMessage(message)
    }

    override suspend fun deleteConversation(conversationId: String) {
        storageAdapter.deleteConversation(conversationId)
    }
}
