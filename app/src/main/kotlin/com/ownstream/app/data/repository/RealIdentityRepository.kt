package com.ownstream.app.data.repository

import com.ownstream.app.data.local.IdentityDao
import com.ownstream.app.data.local.IdentityEntity
import com.ownstream.app.domain.model.Identity
import com.ownstream.app.domain.repository.IdentityRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RealIdentityRepository @Inject constructor(
    private val identityDao: IdentityDao
) : IdentityRepository {

    override suspend fun getLocalIdentity(): Identity? {
        return identityDao.getLocalIdentity()?.toDomain()
    }

    override suspend fun saveIdentity(identity: Identity) {
        identityDao.insertIdentity(identity.toEntity())
    }

    override fun observeLocalIdentity(): Flow<Identity?> {
        return identityDao.observeLocalIdentity().map { it?.toDomain() }
    }
}

fun IdentityEntity.toDomain() = Identity(
    id = id,
    username = username,
    publicKey = publicKey,
    createdAt = createdAt,
    isLocal = isLocal
)

fun Identity.toEntity() = IdentityEntity(
    id = id,
    username = username,
    publicKey = publicKey,
    createdAt = createdAt,
    isLocal = isLocal
)
