package com.ownstream.app.core.di

import com.ownstream.app.core.crypto.CryptoProvider
import com.ownstream.app.core.crypto.SignalCryptoProvider
import com.ownstream.app.core.network.MessageTransport
import com.ownstream.app.core.network.NetworkMessageTransport
import com.ownstream.app.core.storage.EncryptedSecureStorage
import com.ownstream.app.core.storage.SecureStorage
import com.ownstream.app.core.storage.StorageAdapter
import com.ownstream.app.data.local.storage.LocalStorageAdapter
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoreModule {

    @Binds
    @Singleton
    abstract fun bindCryptoProvider(impl: SignalCryptoProvider): CryptoProvider

    @Binds
    @Singleton
    abstract fun bindMessageTransport(impl: NetworkMessageTransport): MessageTransport

    @Binds
    @Singleton
    abstract fun bindSecureStorage(impl: EncryptedSecureStorage): SecureStorage

    @Binds
    @Singleton
    abstract fun bindStorageAdapter(impl: LocalStorageAdapter): StorageAdapter
}
