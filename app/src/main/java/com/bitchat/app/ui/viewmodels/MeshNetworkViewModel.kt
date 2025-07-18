package com.bitchat.app.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bitchat.app.bluetooth.BluetoothManager
import com.bitchat.app.bluetooth.NetworkStatistics
import com.bitchat.app.bluetooth.RelayCapableDeviceInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MeshNetworkViewModel @Inject constructor(
    private val bluetoothManager: BluetoothManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(MeshNetworkUiState())
    val uiState: StateFlow<MeshNetworkUiState> = _uiState.asStateFlow()

    init {
        loadNetworkData()
    }

    fun startDiscovery() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            try {
                val relayDevices = bluetoothManager.discoverAllRelayCapableDevices()
                val statistics = bluetoothManager.getRoutingStatistics()
                
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    relayDevices = relayDevices,
                    statistics = statistics,
                    isNetworkActive = true
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = e.message
                )
            }
        }
    }

    fun refreshNetwork() {
        startDiscovery()
    }

    fun toggleNetwork() {
        viewModelScope.launch {
            val currentState = _uiState.value.isNetworkActive
            
            if (currentState) {
                bluetoothManager.stopAdvancedMeshNetwork()
                _uiState.value = _uiState.value.copy(
                    isNetworkActive = false,
                    relayDevices = emptyList()
                )
            } else {
                val success = bluetoothManager.startAdvancedMeshNetwork()
                if (success) {
                    _uiState.value = _uiState.value.copy(isNetworkActive = true)
                    startDiscovery()
                }
            }
        }
    }

    private fun loadNetworkData() {
        viewModelScope.launch {
            try {
                val statistics = bluetoothManager.getRoutingStatistics()
                _uiState.value = _uiState.value.copy(statistics = statistics)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            }
        }
    }
}

data class MeshNetworkUiState(
    val isLoading: Boolean = false,
    val isNetworkActive: Boolean = false,
    val relayDevices: List<RelayCapableDeviceInfo> = emptyList(),
    val statistics: NetworkStatistics = NetworkStatistics(),
    val error: String? = null
)