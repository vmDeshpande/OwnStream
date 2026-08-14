package com.ownstream.app.feature.conversations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ownstream.app.core.crypto.CryptoProvider
import com.ownstream.app.core.network.ConnectionStatus
import com.ownstream.app.core.network.MessageTransport
import com.ownstream.app.core.network.NetworkMessageTransport
import com.ownstream.app.domain.repository.ChatRepository
import com.ownstream.app.domain.repository.IdentityRepository
import com.ownstream.app.domain.usecase.GetConversationsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ChatListViewModel @Inject constructor(
    private val getConversationsUseCase: GetConversationsUseCase,
    private val chatRepository: ChatRepository,
    private val identityRepository: IdentityRepository,
    private val cryptoProvider: CryptoProvider,
    private val transport: MessageTransport
) : ViewModel() {

    private var lastPreKeyPublishTime = 0L
    private val PREKEY_PUBLISH_INTERVAL_MS = 15 * 60 * 1000 // 15 minutes

    val conversations = getConversationsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val connectionStatus = transport.observeConnectionStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ConnectionStatus.DISCONNECTED)

    val localIdentity = identityRepository.observeLocalIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init {
        // Automatically connect and refresh prekeys when identity is available
        viewModelScope.launch {
            identityRepository.observeLocalIdentity().collectLatest { identity ->
                if (identity != null) {
                    if (transport is NetworkMessageTransport) {
                        transport.connect(identity.id)
                        
                        transport.observeConnectionStatus().collect { status ->
                            if (status == ConnectionStatus.CONNECTED) {
                                val now = System.currentTimeMillis()
                                if (now - lastPreKeyPublishTime > PREKEY_PUBLISH_INTERVAL_MS) {
                                    try {
                                        val bundle = cryptoProvider.getLocalPreKeyBundle()
                                        transport.publishPreKeyBundle(identity.id, bundle)
                                        lastPreKeyPublishTime = now
                                        android.util.Log.i("ChatListVM", "PreKeys refreshed for ${identity.id}")
                                    } catch (e: Exception) {
                                        android.util.Log.e("ChatListVM", "Failed to refresh prekeys", e)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
