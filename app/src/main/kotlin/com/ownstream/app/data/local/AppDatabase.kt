package com.ownstream.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        IdentityEntity::class,
        StorageConfigurationEntity::class,
        ConversationEntity::class,
        ParticipantEntity::class,
        MessageEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun identityDao(): IdentityDao
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
}
