package com.ownstream.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "signal_identities")
data class SignalIdentityEntity(
    @PrimaryKey val id: Int = 1, // Constant 1 for local identity
    val registrationId: Int,
    val encryptedIdentityKeyPair: ByteArray
)

@Entity(tableName = "signal_sessions", primaryKeys = ["addressName", "deviceId"])
data class SignalSessionEntity(
    val addressName: String,
    val deviceId: Int,
    val encryptedSessionRecord: ByteArray
)

@Entity(tableName = "signal_prekeys")
data class SignalPreKeyEntity(
    @PrimaryKey val preKeyId: Int,
    val encryptedRecord: ByteArray
)

@Entity(tableName = "signal_signed_prekeys")
data class SignalSignedPreKeyEntity(
    @PrimaryKey val signedPreKeyId: Int,
    val encryptedRecord: ByteArray
)

@Entity(tableName = "signal_kyber_prekeys")
data class SignalKyberPreKeyEntity(
    @PrimaryKey val kyberPreKeyId: Int,
    val encryptedRecord: ByteArray
)

@Entity(tableName = "signal_trusted_identities", primaryKeys = ["addressName", "deviceId"])
data class SignalTrustedIdentityEntity(
    val addressName: String,
    val deviceId: Int,
    val identityKey: ByteArray, // Public key, stored in plaintext for comparison
    val trustLevel: Int
)
