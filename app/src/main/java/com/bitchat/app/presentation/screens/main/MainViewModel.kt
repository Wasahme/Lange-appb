package com.bitchat.app.presentation.screens.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitchat.app.bluetooth.BluetoothManager
import com.bitchat.app.data.entities.Chat
import com.bitchat.app.data.entities.Device
import com.bitchat.app.data.entities.CallHistory
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val isLoading: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val hasLocationPermission: Boolean = false,
    val recentChats: List<Chat> = emptyList(),
    val nearbyDevices: List<Device> = emptyList(),
    val recentCalls: List<CallHistory> = emptyList(),
    val unreadChatsCount: Int = 0,
    val connectedDevicesCount: Int = 0,
    val errorMessage: String? = null
)

// Temporary data class for call history
data class CallHistory(
    val callId: String,
    val contactName: String,
    val contactId: String,
    val callType: CallType,
    val timestamp: Long,
    val duration: Int, // in seconds
    val isIncoming: Boolean,
    val isAnswered: Boolean
)

enum class CallType {
    VOICE, VIDEO
}

@HiltViewModel
class MainViewModel @Inject constructor(
    private val bluetoothManager: BluetoothManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        observeBluetoothState()
        observeDevices()
        loadInitialData()
    }

    private fun observeBluetoothState() {
        viewModelScope.launch {
            bluetoothManager.isBluetoothEnabled.collect { isEnabled ->
                _uiState.value = _uiState.value.copy(
                    isBluetoothEnabled = isEnabled
                )
            }
        }
    }

    private fun observeDevices() {
        viewModelScope.launch {
            bluetoothManager.discoveredDevices.collect { devices ->
                _uiState.value = _uiState.value.copy(
                    nearbyDevices = devices,
                    connectedDevicesCount = devices.count { it.isOnline }
                )
            }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                // Load recent chats (mock data for now)
                val mockChats = generateMockChats()
                val mockCalls = generateMockCalls()
                
                _uiState.value = _uiState.value.copy(
                    recentChats = mockChats,
                    recentCalls = mockCalls,
                    unreadChatsCount = mockChats.count { it.unreadCount > 0 },
                    hasLocationPermission = true, // This should be checked properly
                    isLoading = false
                )
                
                // Start Bluetooth scanning if enabled
                if (bluetoothManager.isBluetoothSupported() && 
                    bluetoothManager.hasRequiredPermissions()) {
                    startBluetoothScanning()
                }
                
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "فشل في تحميل البيانات: ${e.message}",
                    isLoading = false
                )
            }
        }
    }

    private fun startBluetoothScanning() {
        bluetoothManager.startScanning()
        bluetoothManager.startAdvertising(mapOf(
            "userId" to "current_user_id", // Should come from user repository
            "supportsVoice" to "true",
            "supportsVideo" to "true"
        ))
    }

    fun refreshDevices() {
        viewModelScope.launch {
            if (bluetoothManager.isBluetoothSupported() && 
                bluetoothManager.hasRequiredPermissions()) {
                bluetoothManager.clearDiscoveredDevices()
                bluetoothManager.startScanning()
            }
        }
    }

    fun startNewChat() {
        // Implementation for starting a new chat
        // This could show a device selection dialog
    }

    fun requestPermissions() {
        // This should trigger permission requests in the UI
        // For now, we'll just refresh the state
        bluetoothManager.updateBluetoothState()
    }

    private fun generateMockChats(): List<Chat> {
        return listOf(
            Chat(
                chatId = "chat1",
                chatType = com.bitchat.app.data.entities.ChatType.DIRECT,
                participants = listOf("user1", "user2"),
                chatName = "أحمد محمد",
                lastMessageTimestamp = System.currentTimeMillis() - 300000, // 5 minutes ago
                unreadCount = 3,
                encryptionKey = "encrypted_key_1",
                createdBy = "user1"
            ),
            Chat(
                chatId = "chat2",
                chatType = com.bitchat.app.data.entities.ChatType.DIRECT,
                participants = listOf("user1", "user3"),
                chatName = "فاطمة أحمد",
                lastMessageTimestamp = System.currentTimeMillis() - 600000, // 10 minutes ago
                unreadCount = 0,
                encryptionKey = "encrypted_key_2",
                createdBy = "user1"
            ),
            Chat(
                chatId = "chat3",
                chatType = com.bitchat.app.data.entities.ChatType.GROUP,
                participants = listOf("user1", "user2", "user3", "user4"),
                chatName = "مجموعة العمل",
                lastMessageTimestamp = System.currentTimeMillis() - 3600000, // 1 hour ago
                unreadCount = 7,
                encryptionKey = "encrypted_key_3",
                createdBy = "user1"
            )
        )
    }

    private fun generateMockCalls(): List<CallHistory> {
        return listOf(
            CallHistory(
                callId = "call1",
                contactName = "أحمد محمد",
                contactId = "user2",
                callType = CallType.VIDEO,
                timestamp = System.currentTimeMillis() - 1800000, // 30 minutes ago
                duration = 423, // 7 minutes 3 seconds
                isIncoming = false,
                isAnswered = true
            ),
            CallHistory(
                callId = "call2",
                contactName = "فاطمة أحمد",
                contactId = "user3",
                callType = CallType.VOICE,
                timestamp = System.currentTimeMillis() - 7200000, // 2 hours ago
                duration = 0,
                isIncoming = true,
                isAnswered = false
            ),
            CallHistory(
                callId = "call3",
                contactName = "محمد علي",
                contactId = "user4",
                callType = CallType.VOICE,
                timestamp = System.currentTimeMillis() - 86400000, // 1 day ago
                duration = 156, // 2 minutes 36 seconds
                isIncoming = true,
                isAnswered = true
            )
        )
    }

    override fun onCleared() {
        super.onCleared()
        bluetoothManager.destroy()
    }
}