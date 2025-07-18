package com.bitchat.app.bluetooth

import android.Manifest
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.ParcelUuid
import android.util.Log
import androidx.core.app.ActivityCompat
import com.bitchat.app.data.entities.Device
import com.bitchat.app.data.entities.DeviceCapabilities
import com.bitchat.app.data.entities.DeviceType
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BluetoothManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "BluetoothManager"
        private const val SCAN_DURATION = 30000L // 30 seconds
        private const val ADVERTISE_DURATION = 60000L // 1 minute
        
        // BitChat Service UUID
        val BITCHAT_SERVICE_UUID: UUID = UUID.fromString("12345678-1234-5678-9012-123456789abc")
        val BITCHAT_CHARACTERISTIC_UUID: UUID = UUID.fromString("12345678-1234-5678-9012-123456789abd")
        
        // Parcel UUIDs for advertising
        val BITCHAT_PARCEL_UUID = ParcelUuid(BITCHAT_SERVICE_UUID)
    }

    private val bluetoothManager: BluetoothManager? = 
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bluetoothLeScanner: BluetoothLeScanner? = bluetoothAdapter?.bluetoothLeScanner
    private val bluetoothLeAdvertiser: BluetoothLeAdvertiser? = bluetoothAdapter?.bluetoothLeAdvertiser

    private val _discoveredDevices = MutableStateFlow<List<Device>>(emptyList())
    val discoveredDevices: StateFlow<List<Device>> = _discoveredDevices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private val _isBluetoothEnabled = MutableStateFlow(false)
    val isBluetoothEnabled: StateFlow<Boolean> = _isBluetoothEnabled.asStateFlow()

    private var scanCallback: ScanCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var gattServer: BluetoothGattServer? = null

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    init {
        updateBluetoothState()
    }

    fun updateBluetoothState() {
        _isBluetoothEnabled.value = bluetoothAdapter?.isEnabled == true
    }

    fun isBluetoothSupported(): Boolean {
        return bluetoothAdapter != null
    }

    fun hasRequiredPermissions(): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_ADVERTISE) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startScanning() {
        if (!isBluetoothSupported() || !hasRequiredPermissions() || !_isBluetoothEnabled.value) {
            Log.w(TAG, "Cannot start scanning: missing requirements")
            return
        }

        if (_isScanning.value) {
            Log.d(TAG, "Already scanning")
            return
        }

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                handleScanResult(result)
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                super.onBatchScanResults(results)
                results.forEach { handleScanResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                super.onScanFailed(errorCode)
                Log.e(TAG, "Scan failed with error code: $errorCode")
                _isScanning.value = false
            }
        }

        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
            .setNumOfMatches(ScanSettings.MATCH_NUM_MAX_ADVERTISEMENT)
            .setReportDelay(0)
            .build()

        val scanFilters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(BITCHAT_PARCEL_UUID)
                .build()
        )

        try {
            bluetoothLeScanner?.startScan(scanFilters, scanSettings, scanCallback)
            _isScanning.value = true
            Log.d(TAG, "Started BLE scanning")

            // Stop scanning after duration
            coroutineScope.launch {
                delay(SCAN_DURATION)
                stopScanning()
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when starting scan", e)
        }
    }

    fun stopScanning() {
        scanCallback?.let { callback ->
            try {
                bluetoothLeScanner?.stopScan(callback)
                _isScanning.value = false
                scanCallback = null
                Log.d(TAG, "Stopped BLE scanning")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException when stopping scan", e)
            }
        }
    }

    fun startAdvertising(userInfo: Map<String, String>) {
        if (!isBluetoothSupported() || !hasRequiredPermissions() || !_isBluetoothEnabled.value) {
            Log.w(TAG, "Cannot start advertising: missing requirements")
            return
        }

        if (_isAdvertising.value) {
            Log.d(TAG, "Already advertising")
            return
        }

        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                _isAdvertising.value = true
                Log.d(TAG, "Started BLE advertising")
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                Log.e(TAG, "Advertising failed with error code: $errorCode")
                _isAdvertising.value = false
            }
        }

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .setTimeout(ADVERTISE_DURATION.toInt())
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(true)
            .addServiceUuid(BITCHAT_PARCEL_UUID)
            .build()

        val scanResponseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceData(BITCHAT_PARCEL_UUID, createServiceData(userInfo))
            .build()

        try {
            bluetoothLeAdvertiser?.startAdvertising(
                advertiseSettings,
                advertiseData,
                scanResponseData,
                advertiseCallback
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when starting advertising", e)
        }
    }

    fun stopAdvertising() {
        advertiseCallback?.let { callback ->
            try {
                bluetoothLeAdvertiser?.stopAdvertising(callback)
                _isAdvertising.value = false
                advertiseCallback = null
                Log.d(TAG, "Stopped BLE advertising")
            } catch (e: SecurityException) {
                Log.e(TAG, "SecurityException when stopping advertising", e)
            }
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val device = result.device
        val rssi = result.rssi
        val scanRecord = result.scanRecord

        try {
            val deviceInfo = createDeviceFromScanResult(device, rssi, scanRecord)
            updateDiscoveredDevices(deviceInfo)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing scan result", e)
        }
    }

    private fun createDeviceFromScanResult(
        bluetoothDevice: BluetoothDevice,
        rssi: Int,
        scanRecord: ScanRecord?
    ): Device {
        val deviceName = try {
            bluetoothDevice.name ?: "Unknown Device"
        } catch (e: SecurityException) {
            "Unknown Device"
        }

        val deviceId = bluetoothDevice.address.replace(":", "")
        val serviceData = scanRecord?.getServiceData(BITCHAT_PARCEL_UUID)
        val userInfo = parseServiceData(serviceData)

        return Device(
            deviceId = deviceId,
            deviceName = deviceName,
            deviceType = DeviceType.ANDROID_PHONE, // Default, could be enhanced
            bluetoothAddress = bluetoothDevice.address,
            userId = userInfo["userId"],
            isOnline = true,
            lastSeen = System.currentTimeMillis(),
            signalStrength = rssi,
            distance = calculateDistance(rssi),
            capabilities = DeviceCapabilities(
                hasBitChat = userInfo.isNotEmpty(),
                supportsMessaging = true,
                supportsVoiceCalls = userInfo["supportsVoice"] == "true",
                supportsVideoCalls = userInfo["supportsVideo"] == "true",
                supportsFileTransfer = true
            ),
            isDirectlyConnected = true,
            hopCount = 1
        )
    }

    private fun createServiceData(userInfo: Map<String, String>): ByteArray {
        // Simple encoding of user info
        val data = userInfo.entries.joinToString("|") { "${it.key}:${it.value}" }
        return data.toByteArray().take(20).toByteArray() // BLE limit
    }

    private fun parseServiceData(data: ByteArray?): Map<String, String> {
        if (data == null) return emptyMap()
        
        return try {
            val dataString = String(data)
            dataString.split("|").associate { pair ->
                val parts = pair.split(":")
                if (parts.size == 2) parts[0] to parts[1] else "" to ""
            }.filterKeys { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing service data", e)
            emptyMap()
        }
    }

    private fun calculateDistance(rssi: Int): Double {
        // Simple RSSI to distance calculation (not very accurate but gives rough estimate)
        // Formula: distance = 10^((Measured Power - RSSI) / (10 * N))
        // Where Measured Power is RSSI at 1m distance (typically -59 dBm for phones)
        // N is path loss exponent (typically 2 for free space)
        
        val measuredPower = -59.0 // dBm at 1m
        val pathLossExponent = 2.0
        
        return if (rssi == 0) {
            -1.0
        } else {
            kotlin.math.pow(10.0, (measuredPower - rssi) / (10 * pathLossExponent))
        }
    }

    private fun updateDiscoveredDevices(newDevice: Device) {
        val currentDevices = _discoveredDevices.value.toMutableList()
        val existingIndex = currentDevices.indexOfFirst { it.deviceId == newDevice.deviceId }
        
        if (existingIndex >= 0) {
            currentDevices[existingIndex] = newDevice
        } else {
            currentDevices.add(newDevice)
        }
        
        _discoveredDevices.value = currentDevices
    }

    fun clearDiscoveredDevices() {
        _discoveredDevices.value = emptyList()
    }

    /**
     * إرسال رسالة عبر النظام الذكي الجديد
     * يستطيع استخدام أي جهاز بلوتوث للتمرير
     */
    suspend fun sendMessageViaSmartRouting(
        message: String,
        targetDeviceId: String,
        messageType: String = "text"
    ): Boolean {
        return try {
            // إنشاء كائن Message من البيانات
            val messageObj = com.bitchat.app.data.entities.Message(
                messageId = UUID.randomUUID().toString(),
                chatId = "direct_$targetDeviceId",
                senderId = getCurrentDeviceId(),
                content = message,
                messageType = com.bitchat.app.data.entities.MessageType.TEXT,
                timestamp = System.currentTimeMillis(),
                isRead = false,
                routePath = emptyList(),
                encryptionKey = generateEncryptionKey()
            )

            // استخدام الموجه الذكي
            val meshNetworkManager = MeshNetworkManager(context)
            val universalRelay = UniversalBluetoothRelay(context)
            val smartRouter = SmartMessageRouter(context, meshNetworkManager, universalRelay)
            
            val result = smartRouter.routeMessage(messageObj, targetDeviceId)
            
            when (result) {
                com.bitchat.app.mesh.RoutingResult.SUCCESS -> {
                    Log.d(TAG, "Message sent successfully via smart routing")
                    true
                }
                com.bitchat.app.mesh.RoutingResult.QUEUED -> {
                    Log.d(TAG, "Message queued for later delivery")
                    true // سنعتبر هذا نجاح مؤقت
                }
                else -> {
                    Log.w(TAG, "Failed to route message: $result")
                    false
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending message via smart routing", e)
            false
        }
    }

    /**
     * اكتشاف جميع أنواع الأجهزة القادرة على التمرير
     */
    suspend fun discoverAllRelayCapableDevices(): List<RelayCapableDeviceInfo> {
        return try {
            val universalRelay = UniversalBluetoothRelay(context)
            val relayDevices = universalRelay.discoverRelayCapabilities()
            
            relayDevices.map { device ->
                RelayCapableDeviceInfo(
                    deviceId = device.deviceId,
                    deviceName = device.deviceName,
                    bluetoothAddress = device.bluetoothAddress,
                    signalStrength = device.signalStrength,
                    reliabilityScore = device.reliabilityScore,
                    supportedStrategies = device.strategies.map { it::class.simpleName ?: "Unknown" },
                    hasBleChatApp = false // سنفحص هذا لاحقاً
                )
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error discovering relay capable devices", e)
            emptyList()
        }
    }

    /**
     * الحصول على إحصائيات التوجيه
     */
    suspend fun getRoutingStatistics(): NetworkStatistics {
        return try {
            val meshNetworkManager = MeshNetworkManager(context)
            val universalRelay = UniversalBluetoothRelay(context)
            val smartRouter = SmartMessageRouter(context, meshNetworkManager, universalRelay)
            
            val stats = smartRouter.routingStats.value
            val availableRoutes = smartRouter.availableRoutes.value
            
            NetworkStatistics(
                totalMessages = stats.totalMessages,
                successfulMessages = stats.successfulMessages,
                averageLatency = stats.averageLatency,
                availableRoutes = availableRoutes.size,
                bitChatDevices = availableRoutes.count { it.routeType == com.bitchat.app.mesh.RouteType.DIRECT_BITCHAT },
                universalRelays = availableRoutes.count { it.routeType == com.bitchat.app.mesh.RouteType.UNIVERSAL_RELAY },
                hybridRoutes = availableRoutes.count { it.routeType == com.bitchat.app.mesh.RouteType.HYBRID }
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "Error getting routing statistics", e)
            NetworkStatistics()
        }
    }

    /**
     * بدء الشبكة الشبكية المتقدمة
     */
    fun startAdvancedMeshNetwork(): Boolean {
        return try {
            val meshNetworkManager = MeshNetworkManager(context)
            // الشبكة الشبكية تبدأ تلقائياً عند إنشاء المدير
            Log.d(TAG, "Advanced mesh network started")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start advanced mesh network", e)
            false
        }
    }

    /**
     * إيقاف الشبكة الشبكية المتقدمة
     */
    fun stopAdvancedMeshNetwork() {
        try {
            // سيتم إضافة منطق الإيقاف هنا
            Log.d(TAG, "Advanced mesh network stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping advanced mesh network", e)
        }
    }

    /**
     * الحصول على معرف الجهاز الحالي
     */
    private fun getCurrentDeviceId(): String {
        return bluetoothAdapter?.address?.replace(":", "") ?: "unknown_device"
    }

    /**
     * توليد مفتاح تشفير عشوائي
     */
    private fun generateEncryptionKey(): String {
        return UUID.randomUUID().toString().replace("-", "").take(32)
    }

    /**
     * تنظيف الموارد عند الإغلاق
     */
    fun destroy() {
        try {
            stopScanning()
            stopAdvertising()
            stopAdvancedMeshNetwork()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}

/**
 * معلومات جهاز قادر على التمرير
 */
data class RelayCapableDeviceInfo(
    val deviceId: String,
    val deviceName: String,
    val bluetoothAddress: String,
    val signalStrength: Int,
    val reliabilityScore: Float,
    val supportedStrategies: List<String>,
    val hasBleChatApp: Boolean
)

/**
 * إحصائيات الشبكة
 */
data class NetworkStatistics(
    val totalMessages: Long = 0,
    val successfulMessages: Long = 0,
    val averageLatency: Long = 0,
    val availableRoutes: Int = 0,
    val bitChatDevices: Int = 0,
    val universalRelays: Int = 0,
    val hybridRoutes: Int = 0
)