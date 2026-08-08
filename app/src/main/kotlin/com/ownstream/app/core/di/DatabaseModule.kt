package com.ownstream.app.core.di

import android.content.Context
import androidx.room.Room
import com.ownstream.app.data.local.AppDatabase
import com.ownstream.app.data.local.ConversationDao
import com.ownstream.app.data.local.IdentityDao
import com.ownstream.app.data.local.MessageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "ownstream_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideIdentityDao(database: AppDatabase): IdentityDao = database.identityDao()

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao = database.conversationDao()

    @Provides
    fun provideMessageDao(database: AppDatabase): MessageDao = database.messageDao()
}
