package com.ownstream.app.core.crypto

import com.ownstream.protocol.ProtocolSerialization
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaEncryptionManager @Inject constructor() {
    private val secureRandom = SecureRandom()
    private val ALGORITHM = "AES/GCM/NoPadding"
    private val KEY_SIZE = 32 // 256 bits
    private val IV_SIZE = 12 // 96 bits
    private val TAG_SIZE = 128 // 128 bits

    data class EncryptedFile(
        val data: ByteArray,
        val keyBase64: String,
        val ivBase64: String
    )

    fun encrypt(data: ByteArray): EncryptedFile {
        val key = ByteArray(KEY_SIZE)
        val iv = ByteArray(IV_SIZE)
        secureRandom.nextBytes(key)
        secureRandom.nextBytes(iv)

        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE, iv))
        
        val encryptedData = cipher.doFinal(data)
        
        return EncryptedFile(
            data = encryptedData,
            keyBase64 = ProtocolSerialization.toBase64(key),
            ivBase64 = ProtocolSerialization.toBase64(iv)
        )
    }

    fun decrypt(encryptedData: ByteArray, keyBase64: String, ivBase64: String): ByteArray {
        val key = ProtocolSerialization.fromBase64(keyBase64)
        val iv = ProtocolSerialization.fromBase64(ivBase64)

        val secretKey = SecretKeySpec(key, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_SIZE, iv))
        
        return cipher.doFinal(encryptedData)
    }
}
