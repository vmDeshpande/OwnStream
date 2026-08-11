package com.ownstream.protocol

import kotlinx.serialization.Serializable

/**
 * Device registration DTO.
 */
@Serializable
data class RegisterDeviceRequest(
    val ownStreamId: String,
    val publicKeyP256: ByteArray, // Hardware-backed signing key for auth
    val deviceMetadata: Map<String, String> = emptyMap()
)

/**
 * Authentication challenge request.
 */
@Serializable
data class AuthChallengeRequest(
    val ownStreamId: String
)

/**
 * Authentication challenge response from server.
 */
@Serializable
data class AuthChallengeResponse(
    val nonce: ByteArray
)

/**
 * Authentication login request from client.
 */
@Serializable
data class LoginRequest(
    val ownStreamId: String,
    val nonce: ByteArray,
    val signature: ByteArray // P-256 signature of nonce
)

/**
 * Device/Session information.
 */
@Serializable
data class DeviceSessionInfo(
    val deviceId: Int,
    val registrationId: Int,
    val ownStreamId: String,
    val lastSeen: Long? = null
)

/**
 * Authentication login response from server.
 */
@Serializable
data class LoginResponse(
    val token: String, // JWT or Session Token
    val expiresAt: Long,
    val sessionInfo: DeviceSessionInfo
)

/**
 * Device registration response.
 */
@Serializable
data class RegisterDeviceResponse(
    val sessionInfo: DeviceSessionInfo
)

/**
 * Publish PreKey Bundle request.
 */
@Serializable
data class PublishPreKeyBundleRequest(
    val bundle: ProtocolPreKeyBundle
)

/**
 * Fetch PreKey Bundle response.
 */
@Serializable
data class FetchPreKeyBundleResponse(
    val bundle: ProtocolPreKeyBundle
)
