package com.ownstream.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface IdentityDao {
    @Query("SELECT * FROM identities WHERE isLocal = 1 LIMIT 1")
    suspend fun getLocalIdentity(): IdentityEntity?

    @Query("SELECT * FROM identities WHERE isLocal = 1 LIMIT 1")
    fun observeLocalIdentity(): Flow<IdentityEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertIdentity(identity: IdentityEntity)
}

@Dao
interface ConversationDao {
    @Transaction
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeConversationsWithDetails(): Flow<List<ConversationWithDetails>>

    @Transaction
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversationWithDetails(id: String): ConversationWithDetails?

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStorageConfig(config: StorageConfigurationEntity)

    @Query("SELECT * FROM storage_configurations WHERE conversationId = :id")
    suspend fun getStorageConfig(id: String): StorageConfigurationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipant(participant: ParticipantEntity)

    @Query("SELECT * FROM participants WHERE conversationId = :id")
    suspend fun getParticipants(id: String): List<ParticipantEntity>
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC, id ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun getMessage(id: String): MessageEntity?

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: String)
}

data class ConversationWithDetails(
    @Embedded val conversation: ConversationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "conversationId"
    )
    val storageConfig: StorageConfigurationEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "conversationId"
    )
    val participants: List<ParticipantEntity>
)
