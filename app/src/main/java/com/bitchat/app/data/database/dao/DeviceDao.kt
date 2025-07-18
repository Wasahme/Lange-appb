package com.bitchat.app.data.database.dao

import androidx.room.*
import com.bitchat.app.data.entities.Device
import com.bitchat.app.data.entities.MeshRoute
import com.bitchat.app.data.entities.NetworkTopology
import kotlinx.coroutines.flow.Flow

@Dao
interface DeviceDao {

    @Query("SELECT * FROM devices WHERE deviceId = :deviceId")
    suspend fun getDeviceById(deviceId: String): Device?

    @Query("SELECT * FROM devices WHERE bluetoothAddress = :bluetoothAddress")
    suspend fun getDeviceByBluetoothAddress(bluetoothAddress: String): Device?

    @Query("SELECT * FROM devices WHERE userId = :userId")
    suspend fun getDevicesByUserId(userId: String): List<Device>

    @Query("SELECT * FROM devices")
    fun getAllDevices(): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE isOnline = 1")
    fun getOnlineDevices(): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE isDirectlyConnected = 1")
    fun getDirectlyConnectedDevices(): Flow<List<Device>>

    @Query("SELECT * FROM devices WHERE hopCount <= :maxHops ORDER BY hopCount ASC")
    suspend fun getDevicesWithinHops(maxHops: Int): List<Device>

    @Query("SELECT * FROM devices WHERE distance <= :maxDistance ORDER BY distance ASC")
    suspend fun getDevicesWithinDistance(maxDistance: Double): List<Device>

    @Query("SELECT * FROM devices WHERE signalStrength >= :minSignalStrength ORDER BY signalStrength DESC")
    suspend fun getDevicesBySignalStrength(minSignalStrength: Int): List<Device>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevice(device: Device)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDevices(devices: List<Device>)

    @Update
    suspend fun updateDevice(device: Device)

    @Query("UPDATE devices SET isOnline = :isOnline, lastSeen = :lastSeen WHERE deviceId = :deviceId")
    suspend fun updateDeviceOnlineStatus(deviceId: String, isOnline: Boolean, lastSeen: Long = System.currentTimeMillis())

    @Query("UPDATE devices SET signalStrength = :signalStrength, distance = :distance WHERE deviceId = :deviceId")
    suspend fun updateDeviceConnection(deviceId: String, signalStrength: Int, distance: Double?)

    @Delete
    suspend fun deleteDevice(device: Device)

    @Query("DELETE FROM devices WHERE deviceId = :deviceId")
    suspend fun deleteDeviceById(deviceId: String)

    @Query("DELETE FROM devices WHERE lastSeen < :threshold")
    suspend fun deleteOldDevices(threshold: Long)

    // Mesh Routes
    @Query("SELECT * FROM mesh_routes WHERE destinationDeviceId = :destinationDeviceId AND isActive = 1 ORDER BY routingCost ASC LIMIT 1")
    suspend fun getBestRouteToDevice(destinationDeviceId: String): MeshRoute?

    @Query("SELECT * FROM mesh_routes WHERE destinationDeviceId = :destinationDeviceId")
    suspend fun getAllRoutesToDevice(destinationDeviceId: String): List<MeshRoute>

    @Query("SELECT * FROM mesh_routes WHERE isActive = 1")
    suspend fun getActiveRoutes(): List<MeshRoute>

    @Query("SELECT * FROM mesh_routes WHERE expiresAt < :currentTime")
    suspend fun getExpiredRoutes(currentTime: Long = System.currentTimeMillis()): List<MeshRoute>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeshRoute(route: MeshRoute)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMeshRoutes(routes: List<MeshRoute>)

    @Update
    suspend fun updateMeshRoute(route: MeshRoute)

    @Query("UPDATE mesh_routes SET isActive = 0 WHERE routeId = :routeId")
    suspend fun deactivateRoute(routeId: String)

    @Query("DELETE FROM mesh_routes WHERE expiresAt < :currentTime")
    suspend fun deleteExpiredRoutes(currentTime: Long = System.currentTimeMillis())

    @Query("DELETE FROM mesh_routes WHERE destinationDeviceId = :deviceId")
    suspend fun deleteRoutesToDevice(deviceId: String)

    // Network Topology
    @Query("SELECT * FROM network_topology WHERE topologyId = :topologyId")
    suspend fun getNetworkTopology(topologyId: String): NetworkTopology?

    @Query("SELECT * FROM network_topology ORDER BY lastUpdated DESC LIMIT 1")
    suspend fun getLatestNetworkTopology(): NetworkTopology?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNetworkTopology(topology: NetworkTopology)

    @Update
    suspend fun updateNetworkTopology(topology: NetworkTopology)

    @Query("DELETE FROM network_topology WHERE lastUpdated < :threshold")
    suspend fun deleteOldTopologies(threshold: Long)
}