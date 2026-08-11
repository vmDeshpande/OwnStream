package com.ownstream.app.core.crypto

import java.security.KeyStore
import javax.inject.Inject
import javax.inject.Singleton

interface KeyStoreProvider {
    fun getKeyStore(): KeyStore
}

@Singleton
class AndroidKeyStoreProvider @Inject constructor() : KeyStoreProvider {
    override fun getKeyStore(): KeyStore {
        return KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    }
}
