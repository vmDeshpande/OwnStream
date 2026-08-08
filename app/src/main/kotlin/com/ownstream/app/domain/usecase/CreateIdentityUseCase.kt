package com.ownstream.app.domain.usecase

import com.ownstream.app.core.crypto.CryptoProvider
import com.ownstream.app.domain.repository.IdentityRepository
import javax.inject.Inject

class CreateIdentityUseCase @Inject constructor(
    private val cryptoProvider: CryptoProvider,
    private val identityRepository: IdentityRepository
) {
    suspend operator fun invoke(username: String) {
        val identity = cryptoProvider.generateIdentity(username)
        identityRepository.saveIdentity(identity.copy(isLocal = true))
    }
}
