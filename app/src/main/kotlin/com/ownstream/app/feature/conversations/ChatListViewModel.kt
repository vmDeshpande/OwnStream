package com.ownstream.app.feature.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ownstream.app.domain.model.*
import com.ownstream.app.domain.repository.ChatRepository
import com.ownstream.app.domain.repository.IdentityRepository
import com.ownstream.app.domain.usecase.GetConversationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val getConversationsUseCase: GetConversationsUseCase,
    private val chatRepository: ChatRepository,
    private val identityRepository: IdentityRepository
) : ViewModel() {

    val conversations = getConversationsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun createDemoConversation() {
        viewModelScope.launch {
            val localIdentity = identityRepository.getLocalIdentity() ?: return@launch
            
            val conversationId = UUID.randomUUID().toString()
            val demoConversation = Conversation(
                id = conversationId,
                title = "Local Demo Chat",
                storageConfig = StorageConfiguration(
                    conversationId = conversationId,
                    providerType = StorageProviderType.LOCAL
                ),
                participants = listOf(
                    Participant(localIdentity.id, localIdentity.username, "OWNER"),
                    Participant("demo_bot", "OwnStream Bot", "MEMBER")
                )
            )
            chatRepository.createConversation(demoConversation)
        }
    }
}
