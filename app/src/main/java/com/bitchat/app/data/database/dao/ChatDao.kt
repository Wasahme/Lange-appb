package com.bitchat.app.data.database.dao

import androidx.room.*
import com.bitchat.app.data.entities.Chat
import com.bitchat.app.data.entities.ChatParticipant
import com.bitchat.app.data.entities.ChatType
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {

    @Query("SELECT * FROM chats WHERE chatId = :chatId")
    suspend fun getChatById(chatId: String): Chat?

    @Query("SELECT * FROM chats ORDER BY lastMessageTimestamp DESC")
    fun getAllChats(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE isArchived = 0 ORDER BY isPinned DESC, lastMessageTimestamp DESC")
    fun getActiveChats(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE isArchived = 1 ORDER BY lastMessageTimestamp DESC")
    fun getArchivedChats(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE isPinned = 1 ORDER BY lastMessageTimestamp DESC")
    fun getPinnedChats(): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE chatType = :chatType")
    fun getChatsByType(chatType: ChatType): Flow<List<Chat>>

    @Query("SELECT * FROM chats WHERE chatName LIKE '%' || :searchQuery || '%'")
    suspend fun searchChats(searchQuery: String): List<Chat>

    @Query("SELECT SUM(unreadCount) FROM chats WHERE isMuted = 0")
    suspend fun getTotalUnreadCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChat(chat: Chat)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChats(chats: List<Chat>)

    @Update
    suspend fun updateChat(chat: Chat)

    @Query("UPDATE chats SET lastMessageId = :messageId, lastMessageTimestamp = :timestamp WHERE chatId = :chatId")
    suspend fun updateLastMessage(chatId: String, messageId: String, timestamp: Long)

    @Query("UPDATE chats SET unreadCount = :count WHERE chatId = :chatId")
    suspend fun updateUnreadCount(chatId: String, count: Int)

    @Query("UPDATE chats SET isMuted = :isMuted WHERE chatId = :chatId")
    suspend fun updateMuteStatus(chatId: String, isMuted: Boolean)

    @Query("UPDATE chats SET isPinned = :isPinned WHERE chatId = :chatId")
    suspend fun updatePinStatus(chatId: String, isPinned: Boolean)

    @Query("UPDATE chats SET isArchived = :isArchived WHERE chatId = :chatId")
    suspend fun updateArchiveStatus(chatId: String, isArchived: Boolean)

    @Delete
    suspend fun deleteChat(chat: Chat)

    @Query("DELETE FROM chats WHERE chatId = :chatId")
    suspend fun deleteChatById(chatId: String)

    // Chat Participants
    @Query("SELECT * FROM chat_participants WHERE chatId = :chatId AND isActive = 1")
    suspend fun getChatParticipants(chatId: String): List<ChatParticipant>

    @Query("SELECT * FROM chat_participants WHERE userId = :userId AND isActive = 1")
    suspend fun getUserChats(userId: String): List<ChatParticipant>

    @Query("SELECT * FROM chat_participants WHERE chatId = :chatId AND userId = :userId")
    suspend fun getChatParticipant(chatId: String, userId: String): ChatParticipant?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatParticipant(participant: ChatParticipant)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChatParticipants(participants: List<ChatParticipant>)

    @Update
    suspend fun updateChatParticipant(participant: ChatParticipant)

    @Query("UPDATE chat_participants SET lastReadMessageId = :messageId, lastReadTimestamp = :timestamp WHERE chatId = :chatId AND userId = :userId")
    suspend fun updateLastRead(chatId: String, userId: String, messageId: String, timestamp: Long)

    @Query("UPDATE chat_participants SET isActive = 0 WHERE chatId = :chatId AND userId = :userId")
    suspend fun removeChatParticipant(chatId: String, userId: String)

    @Query("DELETE FROM chat_participants WHERE chatId = :chatId")
    suspend fun deleteAllChatParticipants(chatId: String)
}