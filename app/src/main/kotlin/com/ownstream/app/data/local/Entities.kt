package com.ownstream.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import com.ownstream.app.domain.model.MessageStatus
import com.ownstream.app.domain.model.StorageProviderType

@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey val id: String,
    val username: String,
    val publicKey: String,
    val createdAt: Long,
    val isLocal: Boolean
)

@Entity(
    tableName = "storage_configurations",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class StorageConfigurationEntity(
    @PrimaryKey val conversationId: String,
    val providerType: StorageProviderType,
    val connectionDetails: String // JSON string
)

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String?,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "participants",
    primaryKeys = ["conversationId", "identityId"],
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class ParticipantEntity(
    val conversationId: String,
    val identityId: String,
    val displayName: String?,
    val role: String
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = ConversationEntity::class,
            parentColumns = ["id"],
            childColumns = ["conversationId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val senderId: String,
    val payloadType: String, // "TEXT" or "ENCRYPTED"
    val payloadData: String, // Content or JSON of EncryptedPayload
    val timestamp: Long,
    val status: MessageStatus,
    val sequenceNumber: Long
)
