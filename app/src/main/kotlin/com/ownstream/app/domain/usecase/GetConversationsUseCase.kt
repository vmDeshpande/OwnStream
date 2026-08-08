package com.ownstream.app.domain.usecase

import com.ownstream.app.domain.model.Conversation
import com.ownstream.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetConversationsUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(): Flow<List<Conversation>> {
        return chatRepository.getConversations()
    }
}
