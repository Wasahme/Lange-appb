package com.bitchat.app.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "devices")
@Serializable
data class Device(
    @PrimaryKey
    val deviceId: String,
    val deviceName: String,
    val deviceType: DeviceType,
    val bluetoothAddress: String? = null,
    val wifiAddress: String? = null,
    val ipAddress: String? = null,
    val userId: String? = null, // null if device doesn't have BitChat installed
    val isOnline: Boolean = false,
    val lastSeen: Long = System.currentTimeMillis(),
    val signalStrength: Int = 0, // RSSI value
    val distance: Double? = null, // in meters
    val batteryLevel: Int? = null,
    val capabilities: DeviceCapabilities,
    val location: DeviceLocation? = null,
    val isDirectlyConnected: Boolean = false,
    val hopCount: Int = 0, // Number of hops to reach this device
    val routingPath: List<String> = emptyList(), // Path to reach this device
    val discoveredAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Serializable
data class DeviceCapabilities(
    val hasBitChat: Boolean = false,
    val supportsMessaging: Boolean = false,
    val supportsVoiceCalls: Boolean = false,
    val supportsVideoCalls: Boolean = false,
    val supportsFileTransfer: Boolean = false,
    val maxFileSize: Long = 0,
    val supportedMessageTypes: List<MessageType> = emptyList(),
    val encryptionSupport: List<String> = emptyList()
)

@Serializable
data class DeviceLocation(
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitude: Double? = null,
    val accuracy: Float? = null,
    val timestamp: Long = System.currentTimeMillis()
)

enum class DeviceType {
    ANDROID_PHONE,
    ANDROID_TABLET,
    IPHONE,
    IPAD,
    WINDOWS_PC,
    MAC,
    LINUX_PC,
    RASPBERRY_PI,
    UNKNOWN
}

@Entity(tableName = "mesh_routes")
@Serializable
data class MeshRoute(
    @PrimaryKey
    val routeId: String,
    val destinationDeviceId: String,
    val nextHopDeviceId: String,
    val hopCount: Int,
    val routingCost: Double, // Based on signal strength, battery, etc.
    val isActive: Boolean = true,
    val lastUsed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (30 * 60 * 1000) // 30 minutes
)

@Entity(tableName = "network_topology")
@Serializable
data class NetworkTopology(
    @PrimaryKey
    val topologyId: String,
    val centerDeviceId: String, // Usually current device
    val connectedDevices: List<String>,
    val networkGraph: Map<String, List<String>>, // Adjacency list representation
    val totalDevices: Int,
    val maxHops: Int,
    val networkDensity: Float,
    val lastUpdated: Long = System.currentTimeMillis()
)