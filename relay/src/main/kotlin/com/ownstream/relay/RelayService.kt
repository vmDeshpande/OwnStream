package com.ownstream.relay

import com.ownstream.protocol.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.*

class RelayService {
    private val secureRandom = SecureRandom()
    private val activeChallenges = mutableMapOf<String, ChallengeInfo>()
    private val json = Json { ignoreUnknownKeys = true }

    data class ChallengeInfo(val nonce: ByteArray, val expiry: Long)

    suspend fun registerDevice(request: RegisterDeviceRequest): RegisterDeviceResponse = DatabaseFactory.dbQuery {
        if (!ProtocolValidation.isValidOwnStreamId(request.ownStreamId)) {
            throw IllegalArgumentException("Invalid OwnStream ID format")
        }

        val existingUser = Users.selectAll().where { Users.ownStreamId eq request.ownStreamId }.singleOrNull()
        if (existingUser == null) {
            Users.insert {
                it[ownStreamId] = request.ownStreamId
                it[publicKeyP256Base64] = ProtocolSerialization.toBase64(request.publicKeyP256)
                it[createdAt] = System.currentTimeMillis()
            }
        }

        val deviceId = UUID.randomUUID()
        Devices.insert {
            it[id] = deviceId
            it[ownStreamId] = request.ownStreamId
            it[registrationId] = request.registrationId
        }

        RegisterDeviceResponse(
            sessionInfo = DeviceSessionInfo(1, 0, request.ownStreamId)
        )
    }

    fun generateChallenge(ownStreamId: String): AuthChallengeResponse {
        val nonce = ByteArray(32)
        secureRandom.nextBytes(nonce)
        activeChallenges[ownStreamId] = ChallengeInfo(nonce, System.currentTimeMillis() + 60000)
        return AuthChallengeResponse(nonce)
    }

    suspend fun login(request: LoginRequest): LoginResponse = DatabaseFactory.dbQuery {
        val challenge = activeChallenges[request.ownStreamId] ?: throw IllegalArgumentException("No active challenge")
        if (System.currentTimeMillis() > challenge.expiry) {
            activeChallenges.remove(request.ownStreamId)
            throw IllegalArgumentException("Challenge expired")
        }
        if (!challenge.nonce.contentEquals(request.nonce)) {
            throw IllegalArgumentException("Invalid nonce")
        }

        val user = Users.selectAll().where { Users.ownStreamId eq request.ownStreamId }.singleOrNull()
            ?: throw IllegalArgumentException("User not found")

        val publicKeyBase64 = user[Users.publicKeyP256Base64]
        val publicKeyBytes = ProtocolSerialization.fromBase64(publicKeyBase64)
        if (!verifySignature(publicKeyBytes, request.nonce, request.signature)) {
            throw IllegalArgumentException("Invalid signature")
        }

        activeChallenges.remove(request.ownStreamId)

        // Token generation (mock JWT for now)
        val token = "mockjwt:${request.ownStreamId}:${UUID.randomUUID()}"
        LoginResponse(
            token = token,
            expiresAt = System.currentTimeMillis() + 3600000,
            sessionInfo = DeviceSessionInfo(1, 0, request.ownStreamId)
        )
    }

    suspend fun publishPreKeyBundle(ownStreamId: String, bundle: ProtocolPreKeyBundle) = DatabaseFactory.dbQuery {
        val device = Devices.selectAll().where { Devices.ownStreamId eq ownStreamId }.firstOrNull() 
            ?: throw IllegalStateException("Device not found")
        val deviceId = device[Devices.id]

        PreKeyBundles.upsert(PreKeyBundles.deviceId) {
            it[PreKeyBundles.deviceId] = deviceId
            it[identityKeyBase64] = bundle.identityKeyBase64
            it[signedPreKeyId] = bundle.signedPreKeyId
            it[signedPreKeyPublicBase64] = bundle.signedPreKeyPublicBase64
            it[signedPreKeySignatureBase64] = bundle.signedPreKeySignatureBase64
            it[kyberPreKeyId] = bundle.kyberPreKeyId
            it[kyberPreKeyPublicBase64] = bundle.kyberPreKeyPublicBase64
            it[kyberPreKeySignatureBase64] = bundle.kyberPreKeySignatureBase64
        }

        Devices.update({ Devices.id eq deviceId }) {
            it[registrationId] = bundle.registrationId
            it[lastSeen] = System.currentTimeMillis()
        }

        // Handle one-time prekeys
        bundle.preKeyPublicBase64?.let { base64 ->
            OneTimePreKeys.insert {
                it[OneTimePreKeys.deviceId] = deviceId
                it[keyId] = bundle.preKeyId
                it[keyDataBase64] = base64
            }
        }
    }

    suspend fun fetchPreKeyBundle(ownStreamId: String): FetchPreKeyBundleResponse = DatabaseFactory.dbQuery {
        val device = Devices.selectAll().where { Devices.ownStreamId eq ownStreamId }.firstOrNull()
            ?: throw IllegalArgumentException("Device not found")
        val deviceId = device[Devices.id]

        val bundleRecord = PreKeyBundles.selectAll().where { PreKeyBundles.deviceId eq deviceId }.singleOrNull()
            ?: throw IllegalStateException("PreKey bundle not found for user")

        // Atomic consumption of a one-time prekey
        val oneTimePreKey = OneTimePreKeys.selectAll().where { OneTimePreKeys.deviceId eq deviceId }.firstOrNull()
        if (oneTimePreKey != null) {
            OneTimePreKeys.deleteWhere { OneTimePreKeys.id eq oneTimePreKey[OneTimePreKeys.id] }
        }

        FetchPreKeyBundleResponse(
            bundle = ProtocolPreKeyBundle(
                registrationId = device[Devices.registrationId],
                deviceId = 1, // Simplified
                preKeyId = oneTimePreKey?.get(OneTimePreKeys.keyId) ?: -1,
                preKeyPublicBase64 = oneTimePreKey?.get(OneTimePreKeys.keyDataBase64),
                signedPreKeyId = bundleRecord[PreKeyBundles.signedPreKeyId],
                signedPreKeyPublicBase64 = bundleRecord[PreKeyBundles.signedPreKeyPublicBase64],
                signedPreKeySignatureBase64 = bundleRecord[PreKeyBundles.signedPreKeySignatureBase64],
                identityKeyBase64 = bundleRecord[PreKeyBundles.identityKeyBase64],
                kyberPreKeyId = bundleRecord[PreKeyBundles.kyberPreKeyId],
                kyberPreKeyPublicBase64 = bundleRecord[PreKeyBundles.kyberPreKeyPublicBase64],
                kyberPreKeySignatureBase64 = bundleRecord[PreKeyBundles.kyberPreKeySignatureBase64]
            )
        )
    }

    suspend fun enqueueMessage(envelope: MessageEnvelope) = DatabaseFactory.dbQuery {
        // Idempotency: check if messageId already exists
        val exists = QueuedMessages.selectAll().where { QueuedMessages.messageId eq envelope.messageId }.any()
        if (!exists) {
            val envelopeJson = json.encodeToString(envelope)
            val now = System.currentTimeMillis()
            val expiresAt = now + (30L * 24 * 60 * 60 * 1000) // 30 days
            
            QueuedMessages.insert {
                it[messageId] = envelope.messageId
                it[recipientId] = envelope.recipientId
                it[this.envelopeDataBase64] = ProtocolSerialization.toBase64(envelopeJson.toByteArray())
                it[this.createdAt] = now
                it[this.expiresAt] = expiresAt
            }
        }
    }

    suspend fun fetchQueuedMessages(recipientId: String): List<MessageEnvelope> = DatabaseFactory.dbQuery {
        val now = System.currentTimeMillis()
        QueuedMessages.selectAll()
            .where { (QueuedMessages.recipientId eq recipientId) and (QueuedMessages.expiresAt greater now) }
            .orderBy(QueuedMessages.createdAt to SortOrder.ASC)
            .map {
                val jsonStr = String(ProtocolSerialization.fromBase64(it[QueuedMessages.envelopeDataBase64]))
                json.decodeFromString<MessageEnvelope>(jsonStr)
            }
    }

    suspend fun acknowledgeMessage(recipientId: String, messageId: String) = DatabaseFactory.dbQuery {
        QueuedMessages.deleteWhere { 
            (QueuedMessages.messageId eq messageId) and (QueuedMessages.recipientId eq recipientId)
        }
    }

    private fun verifySignature(publicKeyBytes: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        return try {
            val keyFactory = KeyFactory.getInstance("EC")
            val publicKey = keyFactory.generatePublic(X509EncodedKeySpec(publicKeyBytes))
            val sig = Signature.getInstance("SHA256withECDSA")
            sig.initVerify(publicKey)
            sig.update(data)
            sig.verify(signature)
        } catch (e: Exception) {
            false
        }
    }
}
