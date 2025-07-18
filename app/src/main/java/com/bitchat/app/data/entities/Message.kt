package com.bitchat.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "messages")
@Serializable
data class Message(
    @PrimaryKey
    val messageId: String,
    val chatId: String,
    val senderId: String,
    val receiverId: String,
    val content: MessageContent,
    val messageType: MessageType,
    val timestamp: Long = System.currentTimeMillis(),
    val deliveryStatus: DeliveryStatus = DeliveryStatus.PENDING,
    val isEncrypted: Boolean = true,
    val encryptionKey: String? = null,
    val routingPath: List<String> = emptyList(), // Device IDs in routing path
    val expiresAt: Long? = null, // For temporary messages
    val isEdited: Boolean = false,
    val editedAt: Long? = null,
    val replyToMessageId: String? = null,
    val forwardedFromUserId: String? = null,
    val priority: MessagePriority = MessagePriority.NORMAL
)

@Serializable
data class MessageContent(
    val text: String? = null,
    val fileUrl: String? = null,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val fileMimeType: String? = null,
    val thumbnailUrl: String? = null,
    val duration: Int? = null, // For audio/video in seconds
    val location: MessageLocation? = null,
    val contactCard: ContactCard? = null
)

@Serializable
data class MessageLocation(
    val latitude: Double,
    val longitude: Double,
    val address: String? = null
)

@Serializable
data class ContactCard(
    val name: String,
    val phoneNumber: String? = null,
    val email: String? = null
)

enum class MessageType {
    TEXT, AUDIO, VIDEO, IMAGE, FILE, LOCATION, CONTACT, CALL_START, CALL_END
}

enum class DeliveryStatus {
    PENDING, SENT, DELIVERED, READ, FAILED
}

enum class MessagePriority {
    LOW, NORMAL, HIGH, URGENT
}