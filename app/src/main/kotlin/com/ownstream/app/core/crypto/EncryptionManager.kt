package com.ownstream.app.core.crypto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Handles AES-GCM encryption/decryption for local storage protection.
 * Uses a dedicated key in Android Keystore.
 */
@Singleton
class EncryptionManager @Inject constructor(
    @StorageKeyAlias private val keyAlias: String,
    private val keyStoreProvider: KeyStoreProvider
) {

    private val TRANSFORMATION = "${KeyProperties.KEY_ALGORITHM_AES}/${KeyProperties.BLOCK_MODE_GCM}/${KeyProperties.ENCRYPTION_PADDING_NONE}"
    private val GCM_IV_LENGTH = 12
    private val GCM_TAG_LENGTH = 128

    init {
        createKeyIfNeeded()
    }

    private fun createKeyIfNeeded() {
        println("[6E-Enc] createKeyIfNeeded entry")
        val keyStore = keyStoreProvider.getKeyStore()
        println("[6E-Enc] KeyStore type: ${keyStore.type}")
        if (!keyStore.containsAlias(keyAlias)) {
            println("[6E-Enc] Creating key $keyAlias...")
            if (keyStore.type == "AndroidKeyStore") {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
                val parameterSpec = KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                ).apply {
                    setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    setKeySize(256)
                }.build()
                keyGenerator.init(parameterSpec)
                keyGenerator.generateKey()
            } else {
                // Mock/Standard KeyStore for Unit Tests
                val keyGenerator = KeyGenerator.getInstance("AES")
                keyGenerator.init(256)
                val secretKey = keyGenerator.generateKey()
                keyStore.setKeyEntry(keyAlias, secretKey, null, null)
            }
            println("[6E-Enc] Key created.")
        }
    }

    private fun getSecretKey(): SecretKey {
        println("[6E-Enc] getSecretKey for $keyAlias")
        return keyStoreProvider.getKeyStore().getKey(keyAlias, null) as SecretKey
    }

    fun encrypt(plaintext: ByteArray): ByteArray {
        println("[6E-Enc] encrypting...")
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        println("[6E-Enc] encryption complete.")
        return iv + ciphertext
    }

    fun decrypt(encryptedData: ByteArray): ByteArray {
        if (encryptedData.size < GCM_IV_LENGTH) {
            throw IllegalArgumentException("Encrypted data too short")
        }
        val iv = encryptedData.sliceArray(0 until GCM_IV_LENGTH)
        val ciphertext = encryptedData.sliceArray(GCM_IV_LENGTH until encryptedData.size)
        
        val cipher = Cipher.getInstance(TRANSFORMATION)
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), spec)
        
        return cipher.doFinal(ciphertext)
    }
}
