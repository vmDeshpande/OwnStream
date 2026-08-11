package com.ownstream.relay

import com.ownstream.protocol.*
import kotlinx.datetime.*
import kotlinx.datetime.Clock
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
                it[publicKeyP256] = request.publicKeyP256
            }
        }

        val deviceId = UUID.randomUUID()
        Devices.insert {
            it[id] = deviceId
            it[ownStreamId] = request.ownStreamId
            it[registrationId] = 0
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

        val publicKeyBytes = user[Users.publicKeyP256]
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
            it[identityKey] = bundle.identityKey
            it[signedPreKeyId] = bundle.signedPreKeyId
            it[signedPreKeyPublic] = bundle.signedPreKeyPublic
            it[signedPreKeySignature] = bundle.signedPreKeySignature
            it[kyberPreKeyId] = bundle.kyberPreKeyId
            it[kyberPreKeyPublic] = bundle.kyberPreKeyPublic
            it[kyberPreKeySignature] = bundle.kyberPreKeySignature
        }

        Devices.update({ Devices.id eq deviceId }) {
            it[registrationId] = bundle.registrationId
        }

        // Handle one-time prekeys (simplified)
        bundle.preKeyPublic?.let { preKeyBytes ->
            OneTimePreKeys.insert {
                it[OneTimePreKeys.deviceId] = deviceId
                it[keyId] = bundle.preKeyId
                it[keyData] = preKeyBytes
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
                preKeyPublic = oneTimePreKey?.get(OneTimePreKeys.keyData),
                signedPreKeyId = bundleRecord[PreKeyBundles.signedPreKeyId],
                signedPreKeyPublic = bundleRecord[PreKeyBundles.signedPreKeyPublic],
                signedPreKeySignature = bundleRecord[PreKeyBundles.signedPreKeySignature],
                identityKey = bundleRecord[PreKeyBundles.identityKey],
                kyberPreKeyId = bundleRecord[PreKeyBundles.kyberPreKeyId],
                kyberPreKeyPublic = bundleRecord[PreKeyBundles.kyberPreKeyPublic],
                kyberPreKeySignature = bundleRecord[PreKeyBundles.kyberPreKeySignature]
            )
        )
    }

    suspend fun enqueueMessage(envelope: MessageEnvelope) = DatabaseFactory.dbQuery {
        // Idempotency: check if messageId already exists
        val exists = QueuedMessages.selectAll().where { QueuedMessages.messageId eq envelope.messageId }.any()
        if (!exists) {
            val envelopeData = json.encodeToString(envelope).toByteArray()
            val now = Clock.System.now()
            val expiresAt = now.plus(30, DateTimeUnit.DAY, TimeZone.UTC)
            
            QueuedMessages.insert {
                it[messageId] = envelope.messageId
                it[recipientId] = envelope.recipientId
                it[this.envelopeData] = envelopeData
                it[this.createdAt] = now
                it[this.expiresAt] = expiresAt
            }
        }
    }

    suspend fun fetchQueuedMessages(recipientId: String): List<MessageEnvelope> = DatabaseFactory.dbQuery {
        val now = Clock.System.now()
        QueuedMessages.selectAll()
            .where { (QueuedMessages.recipientId eq recipientId) and (QueuedMessages.expiresAt greater now) }
            .orderBy(QueuedMessages.createdAt to SortOrder.ASC)
            .map {
                json.decodeFromString<MessageEnvelope>(String(it[QueuedMessages.envelopeData]))
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

