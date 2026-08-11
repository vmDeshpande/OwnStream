package com.ownstream.protocol

/**
 * Basic validation for protocol identifiers and sizes.
 */
object ProtocolValidation {
    private val OWNSTREAM_ID_REGEX = Regex("^os_[a-f0-9]{8}$")

    fun isValidOwnStreamId(id: String): Boolean {
        return OWNSTREAM_ID_REGEX.matches(id)
    }

    fun isPayloadSizeValid(size: Int): Boolean {
        return size <= OwnStreamProtocol.MAX_MESSAGE_SIZE_BYTES
    }
}
