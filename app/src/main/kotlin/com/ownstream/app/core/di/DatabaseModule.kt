package com.ownstream.app.core.di

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.ownstream.app.data.local.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `signal_identities` (`id` INTEGER NOT NULL, `registrationId` INTEGER NOT NULL, `encryptedIdentityKeyPair` BLOB NOT NULL, PRIMARY KEY(`id`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `signal_sessions` (`addressName` TEXT NOT NULL, `deviceId` INTEGER NOT NULL, `encryptedSessionRecord` BLOB NOT NULL, PRIMARY KEY(`addressName`, `deviceId`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `signal_prekeys` (`preKeyId` INTEGER NOT NULL, `encryptedRecord` BLOB NOT NULL, PRIMARY KEY(`preKeyId`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `signal_signed_prekeys` (`signedPreKeyId` INTEGER NOT NULL, `encryptedRecord` BLOB NOT NULL, PRIMARY KEY(`signedPreKeyId`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `signal_kyber_prekeys` (`kyberPreKeyId` INTEGER NOT NULL, `encryptedRecord` BLOB NOT NULL, PRIMARY KEY(`kyberPreKeyId`))")
            db.execSQL("CREATE TABLE IF NOT EXISTS `signal_trusted_identities` (`addressName` TEXT NOT NULL, `deviceId` INTEGER NOT NULL, `identityKey` BLOB NOT NULL, `trustLevel` INTEGER NOT NULL, PRIMARY KEY(`addressName`, `deviceId`))")
        }
    }

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ownstream_db"
        )
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideIdentityDao(database: AppDatabase): IdentityDao = database.identityDao()

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()

    @Provides
    fun provideSignalDao(database: AppDatabase): SignalDao = database.signalDao()
}
