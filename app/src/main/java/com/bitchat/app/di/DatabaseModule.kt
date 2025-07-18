package com.bitchat.app.di

import android.content.Context
import androidx.room.Room
import com.bitchat.app.data.database.BitChatDatabase
import com.bitchat.app.data.database.dao.*
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
    fun provideBitChatDatabase(
        @ApplicationContext context: Context
    ): BitChatDatabase {
        // For now, using a simple passphrase. In production, this should be user-generated
        val passphrase = "BitChatSecureDB2024"
        return BitChatDatabase.getDatabase(context, passphrase)
    }

    @Provides
    fun provideUserDao(database: BitChatDatabase): UserDao {
        return database.userDao()
    }

    @Provides
    fun provideMessageDao(database: BitChatDatabase): MessageDao {
        return database.messageDao()
    }

    @Provides
    fun provideChatDao(database: BitChatDatabase): ChatDao {
        return database.chatDao()
    }

    @Provides
    fun provideDeviceDao(database: BitChatDatabase): DeviceDao {
        return database.deviceDao()
    }
}