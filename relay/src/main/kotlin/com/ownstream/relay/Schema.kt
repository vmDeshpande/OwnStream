package com.ownstream.relay

import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.kotlin.datetime.CurrentTimestamp
import org.jetbrains.exposed.sql.kotlin.datetime.timestamp

object Users : Table("users") {
    val ownStreamId = varchar("ownstream_id", 32)
    val publicKeyP256 = binary("public_key_p256")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    override val primaryKey = PrimaryKey(ownStreamId)
}

object Devices : Table("devices") {
    val id = uuid("id")
    val ownStreamId = varchar("ownstream_id", 32) references Users.ownStreamId
    val registrationId = integer("registration_id")
    val deviceMetadata = text("device_metadata").default("{}")
    val lastSeen = timestamp("last_seen").nullable()
    override val primaryKey = PrimaryKey(id)
}

object PreKeyBundles : Table("prekey_bundles") {
    val deviceId = uuid("device_id") references Devices.id
    val identityKey = binary("identity_key")
    val signedPreKeyId = integer("signed_pre_key_id")
    val signedPreKeyPublic = binary("signed_pre_key_public")
    val signedPreKeySignature = binary("signed_pre_key_signature")
    val kyberPreKeyId = integer("kyber_pre_key_id")
    val kyberPreKeyPublic = binary("kyber_pre_key_public")
    val kyberPreKeySignature = binary("kyber_pre_key_signature")
    override val primaryKey = PrimaryKey(deviceId)
}

object OneTimePreKeys : Table("one_time_prekeys") {
    val id = integer("id").autoIncrement()
    val deviceId = uuid("device_id") references Devices.id
    val keyId = integer("key_id")
    val keyData = binary("key_data")
    override val primaryKey = PrimaryKey(id)
}

object QueuedMessages : Table("queued_messages") {
    val messageId = varchar("message_id", 64)
    val recipientId = varchar("recipient_id", 32) references Users.ownStreamId
    val envelopeData = binary("envelope_data")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val expiresAt = timestamp("expires_at")
    override val primaryKey = PrimaryKey(messageId)
}

