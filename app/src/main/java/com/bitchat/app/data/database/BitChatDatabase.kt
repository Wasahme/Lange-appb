package com.bitchat.app.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.bitchat.app.data.entities.*
import com.bitchat.app.data.database.dao.*
import net.sqlcipher.database.SQLiteDatabase
import net.sqlcipher.database.SupportFactory

@Database(
    entities = [
        User::class,
        Message::class,
        Chat::class,
        ChatParticipant::class,
        Device::class,
        MeshRoute::class,
        NetworkTopology::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class BitChatDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
    abstract fun chatDao(): ChatDao
    abstract fun deviceDao(): DeviceDao

    companion object {
        @Volatile
        private var INSTANCE: BitChatDatabase? = null

        fun getDatabase(context: Context, passphrase: String): BitChatDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = buildDatabase(context, passphrase)
                INSTANCE = instance
                instance
            }
        }

        private fun buildDatabase(context: Context, passphrase: String): BitChatDatabase {
            val factory = SupportFactory(SQLiteDatabase.getBytes(passphrase.toCharArray()))
            
            return Room.databaseBuilder(
                context.applicationContext,
                BitChatDatabase::class.java,
                "bitchat_database"
            )
                .openHelperFactory(factory)
                .fallbackToDestructiveMigration()
                .build()
        }

        fun clearInstance() {
            INSTANCE = null
        }
    }
}