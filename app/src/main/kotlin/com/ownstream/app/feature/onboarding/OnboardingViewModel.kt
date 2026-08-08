package com.ownstream.app.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    private val identityRepository: IdentityRepository
) : ViewModel() {

    val localIdentity = identityRepository.observeLocalIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun createIdentity(username: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            createIdentityUseCase(username)
            onComplete()
        }
    }
}
