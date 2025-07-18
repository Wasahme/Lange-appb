package com.bitchat.app.data.database.dao

import androidx.room.*
import com.bitchat.app.data.entities.User
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {

    @Query("SELECT * FROM users WHERE userId = :userId")
    suspend fun getUserById(userId: String): User?

    @Query("SELECT * FROM users WHERE username = :username")
    suspend fun getUserByUsername(username: String): User?

    @Query("SELECT * FROM users WHERE deviceId = :deviceId")
    suspend fun getUserByDeviceId(deviceId: String): User?

    @Query("SELECT * FROM users WHERE bluetoothAddress = :bluetoothAddress")
    suspend fun getUserByBluetoothAddress(bluetoothAddress: String): User?

    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE isOnline = 1")
    fun getOnlineUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE username LIKE '%' || :searchQuery || '%' OR displayName LIKE '%' || :searchQuery || '%'")
    suspend fun searchUsers(searchQuery: String): List<User>

    @Query("SELECT * FROM users ORDER BY lastSeen DESC")
    suspend fun getUsersSortedByLastSeen(): List<User>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUsers(users: List<User>)

    @Update
    suspend fun updateUser(user: User)

    @Query("UPDATE users SET isOnline = :isOnline, lastSeen = :lastSeen WHERE userId = :userId")
    suspend fun updateUserOnlineStatus(userId: String, isOnline: Boolean, lastSeen: Long = System.currentTimeMillis())

    @Query("UPDATE users SET bluetoothAddress = :bluetoothAddress WHERE userId = :userId")
    suspend fun updateUserBluetoothAddress(userId: String, bluetoothAddress: String)

    @Delete
    suspend fun deleteUser(user: User)

    @Query("DELETE FROM users WHERE userId = :userId")
    suspend fun deleteUserById(userId: String)
}