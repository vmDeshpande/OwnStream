package com.ownstream.app.data.local

import androidx.room.TypeConverter
import com.ownstream.app.domain.model.MessageStatus
import com.ownstream.app.domain.model.StorageProviderType

class Converters {
    @TypeConverter
    fun fromStorageProviderType(value: StorageProviderType) = value.name

    @TypeConverter
    fun toStorageProviderType(value: String) = StorageProviderType.valueOf(value)

    @TypeConverter
    fun fromMessageStatus(value: MessageStatus) = value.name

    @TypeConverter
    fun toMessageStatus(value: String) = MessageStatus.valueOf(value)
}
