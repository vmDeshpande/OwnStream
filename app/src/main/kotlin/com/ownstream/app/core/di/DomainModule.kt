package com.ownstream.app.core.di

import com.ownstream.app.data.repository.RealChatRepository
import com.ownstream.app.data.repository.RealIdentityRepository
import com.ownstream.app.domain.repository.ChatRepository
import com.ownstream.app.domain.repository.IdentityRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DomainModule {

    @Binds
    @Singleton
    abstract fun bindIdentityRepository(impl: RealIdentityRepository): IdentityRepository

    @Binds
    @Singleton
    abstract fun bindChatRepository(impl: RealChatRepository): ChatRepository
}
