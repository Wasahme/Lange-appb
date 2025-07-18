package com.bitchat.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "chats")
@Serializable
data class Chat(
    @PrimaryKey
    val chatId: String,
    val chatType: ChatType,
    val participants: List<String>, // User IDs
    val chatName: String? = null,
    val chatImageUrl: String? = null,
    val lastMessageId: String? = null,
    val lastMessageTimestamp: Long = 0,
    val unreadCount: Int = 0,
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val encryptionKey: String,
    val createdBy: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

enum class ChatType {
    DIRECT, GROUP, BROADCAST
}

@Entity(tableName = "chat_participants")
@Serializable
data class ChatParticipant(
    @PrimaryKey
    val id: String,
    val chatId: String,
    val userId: String,
    val role: ParticipantRole = ParticipantRole.MEMBER,
    val joinedAt: Long = System.currentTimeMillis(),
    val lastReadMessageId: String? = null,
    val lastReadTimestamp: Long = 0,
    val isActive: Boolean = true
)

enum class ParticipantRole {
    MEMBER, ADMIN, OWNER
}