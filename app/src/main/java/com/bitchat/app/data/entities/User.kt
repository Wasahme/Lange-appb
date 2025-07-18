package com.bitchat.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "users")
@Serializable
data class User(
    @PrimaryKey
    val userId: String,
    val username: String,
    val displayName: String,
    val profileImageUrl: String? = null,
    val statusMessage: String? = null,
    val publicKey: String,
    val lastSeen: Long = System.currentTimeMillis(),
    val isOnline: Boolean = false,
    val deviceId: String,
    val bluetoothAddress: String? = null,
    val location: UserLocation? = null,
    val privacySettings: PrivacySettings = PrivacySettings(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class UserLocation(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val city: String? = null,
    val country: String? = null,
    val shareLevel: LocationShareLevel = LocationShareLevel.NONE
)

@Serializable
data class PrivacySettings(
    val showLastSeen: Boolean = true,
    val showOnlineStatus: Boolean = true,
    val allowMessagesFrom: MessagePermission = MessagePermission.EVERYONE,
    val shareLocation: LocationShareLevel = LocationShareLevel.NONE,
    val showProfilePicture: Boolean = true
)

enum class LocationShareLevel {
    NONE, CITY_ONLY, APPROXIMATE, EXACT
}

enum class MessagePermission {
    FRIENDS_ONLY, EVERYONE, NONE
}