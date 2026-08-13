package com.ownstream.app.domain.usecase

import com.ownstream.app.core.crypto.CryptoProvider
import com.ownstream.app.domain.repository.IdentityRepository
import javax.inject.Inject

class CreateIdentityUseCase @Inject constructor(
    private val cryptoProvider: CryptoProvider,
    private val identityRepository: IdentityRepository
) {
    suspend operator fun invoke(username: String): com.ownstream.app.domain.model.Identity {
        val identity = cryptoProvider.generateIdentity(username)
        val localIdentity = identity.copy(isLocal = true)
        identityRepository.saveIdentity(localIdentity)
        return localIdentity
    }
}
