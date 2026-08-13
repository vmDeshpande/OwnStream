package com.ownstream.protocol

import java.util.Base64

/**
 * Common utilities for protocol-level serialization.
 */
object ProtocolSerialization {
    fun toBase64(data: ByteArray): String {
        return Base64.getEncoder().encodeToString(data)
    }

    fun fromBase64(base64: String): ByteArray {
        return Base64.getDecoder().decode(base64)
    }
}
