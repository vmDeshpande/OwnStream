package com.ownstream.app.domain.usecase

import com.ownstream.app.domain.model.Message
import com.ownstream.app.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetMessagesUseCase @Inject constructor(
    private val chatRepository: ChatRepository
) {
    operator fun invoke(conversationId: String): Flow<List<Message>> {
        return chatRepository.getMessages(conversationId)
    }
}
