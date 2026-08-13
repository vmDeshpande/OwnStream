package com.ownstream.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ownstream.app.core.crypto.CryptoProvider
import com.ownstream.app.core.network.MessageTransport
import com.ownstream.app.domain.model.Identity
import com.ownstream.app.domain.repository.IdentityRepository
import com.ownstream.app.domain.usecase.CreateIdentityUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val createIdentityUseCase: CreateIdentityUseCase,
    private val identityRepository: IdentityRepository,
    private val cryptoProvider: CryptoProvider,
    private val transport: MessageTransport
) : ViewModel() {

    val localIdentity = identityRepository.observeLocalIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun createIdentity(username: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            val identity = createIdentityUseCase(username)
            // Publish prekeys right after identity creation
            try {
                val bundle = cryptoProvider.getLocalPreKeyBundle()
                transport.publishPreKeyBundle(identity.id, bundle)
            } catch (e: Exception) {
                // Log error but continue onboarding, we'll retry later if needed
            }
            onComplete()
        }
    }
}

