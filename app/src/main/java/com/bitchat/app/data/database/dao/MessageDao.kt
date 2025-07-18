package com.bitchat.app.data.database.dao

import androidx.room.*
import com.bitchat.app.data.entities.Message
import com.bitchat.app.data.entities.DeliveryStatus
import com.bitchat.app.data.entities.MessageType
import kotlinx.coroutines.flow.Flow

@Dao
interface MessageDao {

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp ASC")
    fun getMessagesByChatId(chatId: String): Flow<List<Message>>

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    suspend fun getPagedMessagesByChatId(chatId: String, limit: Int, offset: Int): List<Message>

    @Query("SELECT * FROM messages WHERE messageId = :messageId")
    suspend fun getMessageById(messageId: String): Message?

    @Query("SELECT * FROM messages WHERE deliveryStatus = :status")
    suspend fun getMessagesByStatus(status: DeliveryStatus): List<Message>

    @Query("SELECT * FROM messages WHERE senderId = :senderId AND receiverId = :receiverId")
    suspend fun getMessagesBetweenUsers(senderId: String, receiverId: String): List<Message>

    @Query("SELECT * FROM messages WHERE messageType = :type AND chatId = :chatId")
    suspend fun getMessagesByTypeInChat(type: MessageType, chatId: String): List<Message>

    @Query("SELECT * FROM messages WHERE expiresAt IS NOT NULL AND expiresAt < :currentTime")
    suspend fun getExpiredMessages(currentTime: Long = System.currentTimeMillis()): List<Message>

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :searchQuery || '%' AND chatId = :chatId")
    suspend fun searchMessagesInChat(searchQuery: String, chatId: String): List<Message>

    @Query("SELECT * FROM messages WHERE content LIKE '%' || :searchQuery || '%'")
    suspend fun searchAllMessages(searchQuery: String): List<Message>

    @Query("SELECT COUNT(*) FROM messages WHERE chatId = :chatId AND deliveryStatus != 'READ' AND senderId != :currentUserId")
    suspend fun getUnreadMessageCount(chatId: String, currentUserId: String): Int

    @Query("SELECT * FROM messages WHERE chatId = :chatId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastMessageInChat(chatId: String): Message?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: Message)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<Message>)

    @Update
    suspend fun updateMessage(message: Message)

    @Query("UPDATE messages SET deliveryStatus = :status WHERE messageId = :messageId")
    suspend fun updateMessageStatus(messageId: String, status: DeliveryStatus)

    @Query("UPDATE messages SET deliveryStatus = 'read' WHERE chatId = :chatId AND senderId != :currentUserId")
    suspend fun markAllMessagesAsRead(chatId: String, currentUserId: String)

    @Delete
    suspend fun deleteMessage(message: Message)

    @Query("DELETE FROM messages WHERE messageId = :messageId")
    suspend fun deleteMessageById(messageId: String)

    @Query("DELETE FROM messages WHERE chatId = :chatId")
    suspend fun deleteAllMessagesInChat(chatId: String)

    @Query("DELETE FROM messages WHERE expiresAt IS NOT NULL AND expiresAt < :currentTime")
    suspend fun deleteExpiredMessages(currentTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM messages WHERE timestamp < :beforeTimestamp")
    suspend fun deleteOldMessages(beforeTimestamp: Long)
}