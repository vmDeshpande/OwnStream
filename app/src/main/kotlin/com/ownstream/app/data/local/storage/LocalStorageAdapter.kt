package com.ownstream.app.data.local.storage

import com.ownstream.protocol.EncryptedPayload
import com.ownstream.app.core.storage.StorageAdapter
import com.ownstream.app.data.local.*
import com.ownstream.app.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject

class LocalStorageAdapter @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao
) : StorageAdapter {

    private val json = Json { ignoreUnknownKeys = true }

    override fun observeConversations(): Flow<List<Conversation>> {
        return conversationDao.observeConversationsWithDetails().map { list ->
            list.map { it.toDomain(json) }
        }
    }

    override fun observeMessages(conversationId: String): Flow<List<Message>> {
        return messageDao.observeMessages(conversationId).map { list ->
            list.map { it.toDomain(json) }
        }
    }

    override suspend fun getConversation(conversationId: String): Conversation? {
        return conversationDao.getConversationWithDetails(conversationId)?.toDomain(json)
    }

    override suspend fun saveConversation(conversation: Conversation) {
        conversationDao.insertConversation(conversation.toEntity())
        conversationDao.insertStorageConfig(conversation.storageConfig.toEntity(json))
        conversation.participants.forEach { 
            conversationDao.insertParticipant(it.toEntity(conversation.id))
        }
    }

    override suspend fun deleteConversation(conversationId: String) {
        conversationDao.deleteConversation(conversationId)
    }

    override suspend fun saveMessage(message: Message) {
        messageDao.insertMessage(message.toEntity(json))
    }

    override suspend fun getMessage(messageId: String): Message? {
        return messageDao.getMessage(messageId)?.toDomain(json)
    }

    override suspend fun deleteMessage(messageId: String) {
        messageDao.deleteMessage(messageId)
    }

    override suspend fun getStorageConfiguration(conversationId: String): StorageConfiguration? {
        return conversationDao.getStorageConfig(conversationId)?.toDomain(json)
    }

    override suspend fun saveStorageConfiguration(config: StorageConfiguration) {
        conversationDao.insertStorageConfig(config.toEntity(json))
    }
}

// Mapper extensions
fun ConversationWithDetails.toDomain(json: Json): Conversation {
    return Conversation(
        id = conversation.id,
        title = conversation.title,
        storageConfig = storageConfig.toDomain(json),
        createdAt = conversation.createdAt,
        updatedAt = conversation.updatedAt,
        participants = participants.map { it.toDomain() }
    )
}

fun StorageConfigurationEntity.toDomain(json: Json): StorageConfiguration {
    return StorageConfiguration(
        conversationId = conversationId,
        providerType = providerType,
        connectionDetails = json.decodeFromString(connectionDetails)
    )
}

fun ParticipantEntity.toDomain(): Participant {
    return Participant(
        identityId = identityId,
        displayName = displayName,
        role = role
    )
}

fun MessageEntity.toDomain(json: Json): Message {
    val payload = when (payloadType) {
        "TEXT" -> MessagePayload.Text(payloadData)
        "ENCRYPTED" -> MessagePayload.Encrypted(json.decodeFromString<EncryptedPayload>(payloadData))
        else -> MessagePayload.Text("Unknown payload type")
    }
    return Message(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        payload = payload,
        timestamp = timestamp,
        status = status,
        sequenceNumber = sequenceNumber
    )
}

fun Conversation.toEntity() = ConversationEntity(
    id = id,
    title = title,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun StorageConfiguration.toEntity(json: Json) = StorageConfigurationEntity(
    conversationId = conversationId,
    providerType = providerType,
    connectionDetails = json.encodeToString(connectionDetails)
)

fun Participant.toEntity(conversationId: String) = ParticipantEntity(
    conversationId = conversationId,
    identityId = identityId,
    displayName = displayName,
    role = role
)

fun Message.toEntity(json: Json): MessageEntity {
    val (type, data) = when (val p = payload) {
        is MessagePayload.Text -> "TEXT" to p.content
        is MessagePayload.Encrypted -> "ENCRYPTED" to json.encodeToString(p.encryptedPayload)
    }
    return MessageEntity(
        id = id,
        conversationId = conversationId,
        senderId = senderId,
        payloadType = type,
        payloadData = data,
        timestamp = timestamp,
        status = status,
        sequenceNumber = sequenceNumber
    )
}
