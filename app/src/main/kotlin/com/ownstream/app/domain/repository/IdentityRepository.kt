package com.ownstream.app.domain.repository

import com.ownstream.app.domain.model.Identity
import kotlinx.coroutines.flow.Flow

interface IdentityRepository {
    suspend fun getLocalIdentity(): Identity?
    suspend fun saveIdentity(identity: Identity)
    fun observeLocalIdentity(): Flow<Identity?>
}
