package com.ownstream.relay

import org.jetbrains.exposed.sql.Table

object Users : Table("users") {
    val ownStreamId = varchar("ownstream_id", 32)
    val publicKeyP256Base64 = text("public_key_p256_base64")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(ownStreamId)
}

object Devices : Table("devices") {
    val id = uuid("id")
    val ownStreamId = varchar("ownstream_id", 32) references Users.ownStreamId
    val registrationId = integer("registration_id")
    val deviceMetadata = text("device_metadata").default("{}")
    val lastSeen = long("last_seen").nullable()
    override val primaryKey = PrimaryKey(id)
}

object PreKeyBundles : Table("prekey_bundles") {
    val deviceId = uuid("device_id") references Devices.id
    val identityKeyBase64 = text("identity_key_base64")
    val signedPreKeyId = integer("signed_pre_key_id")
    val signedPreKeyPublicBase64 = text("signed_pre_key_public_base64")
    val signedPreKeySignatureBase64 = text("signed_pre_key_signature_base64")
    val kyberPreKeyId = integer("kyber_pre_key_id")
    val kyberPreKeyPublicBase64 = text("kyber_pre_key_public_base64")
    val kyberPreKeySignatureBase64 = text("kyber_pre_key_signature_base64")
    override val primaryKey = PrimaryKey(deviceId)
}

object OneTimePreKeys : Table("one_time_prekeys") {
    val id = integer("id").autoIncrement()
    val deviceId = uuid("device_id") references Devices.id
    val keyId = integer("key_id")
    val keyDataBase64 = text("key_data_base64")
    override val primaryKey = PrimaryKey(id)
}

object QueuedMessages : Table("queued_messages") {
    val messageId = varchar("message_id", 64)
    val recipientId = varchar("recipient_id", 32) references Users.ownStreamId
    val envelopeDataBase64 = text("envelope_data_base64")
    val createdAt = long("created_at")
    val expiresAt = long("expires_at")
    override val primaryKey = PrimaryKey(messageId)
}

object MediaFiles : Table("media_files") {
    val id = varchar("id", 64)
    val ownerId = varchar("owner_id", 32) references Users.ownStreamId
    val fileName = text("file_name")
    val encryptedDataBase64 = text("encrypted_data_base64")
    val createdAt = long("created_at")
    override val primaryKey = PrimaryKey(id)
}

