package com.ownstream.app.core.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

interface SecureStorage {
    fun saveAuthToken(token: String)
    fun getAuthToken(): String?
    fun clearAuthToken()
}

@Singleton
class EncryptedSecureStorage @Inject constructor(
    @ApplicationContext private val context: Context
) : SecureStorage {
    private val sharedPreferences: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            "ownstream_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    override fun saveAuthToken(token: String) {
        sharedPreferences.edit().putString("auth_token", token).apply()
    }

    override fun getAuthToken(): String? {
        return sharedPreferences.getString("auth_token", null)
    }

    override fun clearAuthToken() {
        sharedPreferences.edit().remove("auth_token").apply()
    }
}
