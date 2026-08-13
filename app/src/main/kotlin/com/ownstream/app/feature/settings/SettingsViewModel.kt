package com.ownstream.app.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ownstream.app.core.network.RelayConfig
import com.ownstream.app.domain.repository.IdentityRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val relayConfig: RelayConfig,
    private val identityRepository: IdentityRepository
) : ViewModel() {

    val localIdentity = identityRepository.observeLocalIdentity()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun getRelayUrl(): String = relayConfig.baseUrl

    fun updateRelayUrl(url: String) {
        relayConfig.baseUrl = url
    }
}
