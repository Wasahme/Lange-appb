package com.bitchat.app.mesh

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * نظام التمرير العام عبر البلوتوث
 * يستطيع استخدام أي جهاز بلوتوث كنقطة تمرير حتى لو لم يكن مثبت عليه BitChat
 */
@Singleton
class UniversalBluetoothRelay @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "UniversalBTRelay"
        
        // استراتيجيات مختلفة للتمرير
        const val STRATEGY_DIRECT_GATT = 1
        const val STRATEGY_UART_SERVICE = 2
        const val STRATEGY_ADVERTISING_CHUNKS = 3
        const val STRATEGY_DEVICE_NAME_ENCODING = 4
        const val STRATEGY_MANUFACTURER_DATA = 5
        const val STRATEGY_SERVICE_DATA_HIJACK = 6
        
        // UUIDs للخدمات الشائعة التي يمكن "استعارتها"
        val COMMON_SERVICES = mapOf(
            "Nordic UART" to UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"),
            "Heart Rate" to UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb"),
            "Battery Service" to UUID.fromString("0000180f-0000-1000-8000-00805f9b34fb"),
            "Device Information" to UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"),
            "Generic Access" to UUID.fromString("00001800-0000-1000-8000-00805f9b34fb"),
            "Eddystone" to UUID.fromString("0000feaa-0000-1000-8000-00805f9b34fb")
        )
        
        const val CHUNK_SIZE = 18 // حد البيانات في advertising
        const val MAX_RETRIES = 3
        const val RELAY_DISCOVERY_TIME = 10000L // 10 ثواني
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    private val bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val relayStrategies = mutableMapOf<String, RelayStrategy>()
    private val deviceCapabilities = mutableMapOf<String, DeviceCapabilities>()

    /**
     * اكتشاف قدرات الأجهزة المتاحة للتمرير
     */
    suspend fun discoverRelayCapabilities(): List<RelayCapableDevice> = withContext(Dispatchers.IO) {
        val discoveredDevices = mutableListOf<RelayCapableDevice>()
        val scanDeferred = CompletableDeferred<Unit>()

        val scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = analyzeDeviceCapabilities(result)
                if (device.strategies.isNotEmpty()) {
                    discoveredDevices.add(device)
                }
            }
        }

        try {
            bluetoothLeScanner?.startScan(scanCallback)
            delay(RELAY_DISCOVERY_TIME)
            bluetoothLeScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Permission error during discovery", e)
        }

        scanDeferred.complete(Unit)
        return@withContext discoveredDevices.distinctBy { it.deviceId }
    }

    /**
     * تحليل قدرات جهاز معين للتمرير
     */
    private fun analyzeDeviceCapabilities(result: ScanResult): RelayCapableDevice {
        val device = result.device
        val scanRecord = result.scanRecord
        val strategies = mutableListOf<RelayStrategy>()

        // Strategy 1: فحص GATT Services المتاحة
        scanRecord?.serviceUuids?.forEach { serviceUuid ->
            when {
                COMMON_SERVICES.values.contains(serviceUuid.uuid) -> {
                    strategies.add(RelayStrategy.GattService(serviceUuid.uuid))
                }
                serviceUuid.uuid.toString().startsWith("0000") -> {
                    // خدمة بلوتوث رسمية - يمكن الاتصال بها
                    strategies.add(RelayStrategy.GattService(serviceUuid.uuid))
                }
            }
        }

        // Strategy 2: فحص إمكانية تشفير اسم الجهاز
        if (device.name != null && device.name.length >= 8) {
            strategies.add(RelayStrategy.DeviceNameEncoding)
        }

        // Strategy 3: فحص Manufacturer Data
        scanRecord?.manufacturerSpecificData?.let { manufacturerData ->
            if (manufacturerData.size() > 0) {
                strategies.add(RelayStrategy.ManufacturerDataHijack)
            }
        }

        // Strategy 4: فحص Service Data
        scanRecord?.serviceData?.let { serviceData ->
            if (serviceData.isNotEmpty()) {
                strategies.add(RelayStrategy.ServiceDataHijack)
            }
        }

        // Strategy 5: Advertising-based relay
        strategies.add(RelayStrategy.AdvertisingRelay)

        return RelayCapableDevice(
            deviceId = device.address.replace(":", ""),
            bluetoothAddress = device.address,
            deviceName = device.name ?: "Unknown",
            signalStrength = result.rssi,
            strategies = strategies,
            reliabilityScore = calculateReliabilityScore(strategies, result.rssi)
        )
    }

    /**
     * حساب درجة موثوقية الجهاز كنقطة تمرير
     */
    private fun calculateReliabilityScore(strategies: List<RelayStrategy>, rssi: Int): Float {
        var score = 0f
        
        // نقاط لكل استراتيجية
        strategies.forEach { strategy ->
            score += when (strategy) {
                is RelayStrategy.GattService -> 10f
                is RelayStrategy.DeviceNameEncoding -> 3f
                is RelayStrategy.ManufacturerDataHijack -> 7f
                is RelayStrategy.ServiceDataHijack -> 8f
                is RelayStrategy.AdvertisingRelay -> 5f
            }
        }

        // تعديل حسب قوة الإشارة
        val signalBonus = when {
            rssi > -50 -> 5f
            rssi > -70 -> 3f
            rssi > -80 -> 1f
            else -> 0f
        }

        return (score + signalBonus) / strategies.size
    }

    /**
     * إرسال رسالة عبر جهاز تمرير محدد
     */
    suspend fun relayMessageThroughDevice(
        message: ByteArray,
        relayDevice: RelayCapableDevice,
        targetDeviceId: String
    ): Boolean = withContext(Dispatchers.IO) {
        
        // جرب الاستراتيجيات بترتيب الموثوقية
        val sortedStrategies = relayDevice.strategies.sortedByDescending { strategy ->
            when (strategy) {
                is RelayStrategy.GattService -> 10
                is RelayStrategy.ServiceDataHijack -> 8
                is RelayStrategy.ManufacturerDataHijack -> 7
                is RelayStrategy.AdvertisingRelay -> 5
                is RelayStrategy.DeviceNameEncoding -> 3
            }
        }

        for (strategy in sortedStrategies) {
            try {
                val success = when (strategy) {
                    is RelayStrategy.GattService -> 
                        relayViaGattService(relayDevice, message, strategy.serviceUuid)
                    is RelayStrategy.DeviceNameEncoding -> 
                        relayViaDeviceNameEncoding(relayDevice, message)
                    is RelayStrategy.ManufacturerDataHijack -> 
                        relayViaManufacturerData(relayDevice, message)
                    is RelayStrategy.ServiceDataHijack -> 
                        relayViaServiceData(relayDevice, message)
                    is RelayStrategy.AdvertisingRelay -> 
                        relayViaAdvertising(relayDevice, message)
                }

                if (success) {
                    Log.d(TAG, "Successfully relayed via ${strategy::class.simpleName}")
                    return@withContext true
                }
            } catch (e: Exception) {
                Log.w(TAG, "Strategy ${strategy::class.simpleName} failed", e)
            }
        }

        return@withContext false
    }

    /**
     * التمرير عبر GATT Service
     */
    private suspend fun relayViaGattService(
        relayDevice: RelayCapableDevice,
        message: ByteArray,
        serviceUuid: UUID
    ): Boolean = withContext(Dispatchers.IO) {
        
        var gatt: BluetoothGatt? = null
        val connectionDeferred = CompletableDeferred<Boolean>()

        try {
            val device = bluetoothAdapter.getRemoteDevice(relayDevice.bluetoothAddress)
            
            val gattCallback = object : BluetoothGattCallback() {
                override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
                    when (newState) {
                        BluetoothProfile.STATE_CONNECTED -> {
                            gatt?.discoverServices()
                        }
                        BluetoothProfile.STATE_DISCONNECTED -> {
                            if (!connectionDeferred.isCompleted) {
                                connectionDeferred.complete(false)
                            }
                        }
                    }
                }

                override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        val service = gatt?.getService(serviceUuid)
                        val characteristic = service?.characteristics?.firstOrNull { char ->
                            char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0
                        }

                        if (characteristic != null) {
                            // تشفير الرسالة كبيانات "عادية"
                            val encodedMessage = encodeMessageAsNormalData(message)
                            characteristic.value = encodedMessage
                            
                            val writeSuccess = gatt?.writeCharacteristic(characteristic) ?: false
                            if (!writeSuccess) {
                                connectionDeferred.complete(false)
                            }
                        } else {
                            connectionDeferred.complete(false)
                        }
                    } else {
                        connectionDeferred.complete(false)
                    }
                }

                override fun onCharacteristicWrite(
                    gatt: BluetoothGatt?,
                    characteristic: BluetoothGattCharacteristic?,
                    status: Int
                ) {
                    connectionDeferred.complete(status == BluetoothGatt.GATT_SUCCESS)
                }
            }

            gatt = device.connectGatt(context, false, gattCallback)
            
            return@withContext withTimeoutOrNull(5000) {
                connectionDeferred.await()
            } ?: false

        } catch (e: Exception) {
            Log.e(TAG, "GATT relay failed", e)
            return@withContext false
        } finally {
            try {
                gatt?.disconnect()
                gatt?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing GATT", e)
            }
        }
    }

    /**
     * التمرير عبر تشفير اسم الجهاز
     */
    private suspend fun relayViaDeviceNameEncoding(
        relayDevice: RelayCapableDevice,
        message: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        
        try {
            // تشفير الرسالة في اسم الجهاز
            val encodedName = encodeMessageInDeviceName(message)
            
            // محاولة "اختطاف" اسم الجهاز مؤقتاً عبر advertising
            val advertiseData = AdvertiseData.Builder()
                .setIncludeDeviceName(false) // لا نريد الاسم الحقيقي
                .addServiceData(
                    ParcelUuid(UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb")),
                    encodedName.toByteArray()
                )
                .build()

            val advertiseSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTimeout(3000) // 3 ثواني فقط
                .build()

            val advertiseDeferred = CompletableDeferred<Boolean>()
            
            val advertiseCallback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    advertiseDeferred.complete(true)
                }
                override fun onStartFailure(errorCode: Int) {
                    advertiseDeferred.complete(false)
                }
            }

            bluetoothLeAdvertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
            
            val result = withTimeoutOrNull(4000) {
                advertiseDeferred.await()
            } ?: false

            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            return@withContext result

        } catch (e: Exception) {
            Log.e(TAG, "Device name encoding failed", e)
            return@withContext false
        }
    }

    /**
     * التمرير عبر Manufacturer Data
     */
    private suspend fun relayViaManufacturerData(
        relayDevice: RelayCapableDevice,
        message: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        
        try {
            // تقسيم الرسالة لقطع صغيرة
            val chunks = message.toList().chunked(CHUNK_SIZE)
            var allChunksSent = true

            for (i in chunks.indices) {
                val chunk = chunks[i].toByteArray()
                val manufacturerId = 0xFFFF // معرف عام
                
                val advertiseData = AdvertiseData.Builder()
                    .addManufacturerData(manufacturerId, createChunkData(chunk, i, chunks.size))
                    .build()

                val advertiseSettings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTimeout(2000)
                    .build()

                val chunkDeferred = CompletableDeferred<Boolean>()
                
                val advertiseCallback = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                        chunkDeferred.complete(true)
                    }
                    override fun onStartFailure(errorCode: Int) {
                        chunkDeferred.complete(false)
                    }
                }

                bluetoothLeAdvertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
                
                val chunkResult = withTimeoutOrNull(3000) {
                    chunkDeferred.await()
                } ?: false

                bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
                
                if (!chunkResult) {
                    allChunksSent = false
                    break
                }

                // تأخير بسيط بين القطع
                delay(100)
            }

            return@withContext allChunksSent

        } catch (e: Exception) {
            Log.e(TAG, "Manufacturer data relay failed", e)
            return@withContext false
        }
    }

    /**
     * التمرير عبر Service Data
     */
    private suspend fun relayViaServiceData(
        relayDevice: RelayCapableDevice,
        message: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        
        try {
            // استخدام خدمة Eddystone كغطاء
            val eddystoneUuid = ParcelUuid(UUID.fromString("0000feaa-0000-1000-8000-00805f9b34fb"))
            
            val chunks = message.toList().chunked(CHUNK_SIZE)
            var allChunksSent = true

            for (i in chunks.indices) {
                val chunk = chunks[i].toByteArray()
                
                val advertiseData = AdvertiseData.Builder()
                    .addServiceData(eddystoneUuid, createChunkData(chunk, i, chunks.size))
                    .build()

                val advertiseSettings = AdvertiseSettings.Builder()
                    .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                    .setTimeout(2000)
                    .build()

                val chunkDeferred = CompletableDeferred<Boolean>()
                
                val advertiseCallback = object : AdvertiseCallback() {
                    override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                        chunkDeferred.complete(true)
                    }
                    override fun onStartFailure(errorCode: Int) {
                        chunkDeferred.complete(false)
                    }
                }

                bluetoothLeAdvertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
                
                val chunkResult = withTimeoutOrNull(3000) {
                    chunkDeferred.await()
                } ?: false

                bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
                
                if (!chunkResult) {
                    allChunksSent = false
                    break
                }

                delay(100)
            }

            return@withContext allChunksSent

        } catch (e: Exception) {
            Log.e(TAG, "Service data relay failed", e)
            return@withContext false
        }
    }

    /**
     * التمرير عبر Advertising العادي
     */
    private suspend fun relayViaAdvertising(
        relayDevice: RelayCapableDevice,
        message: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        
        try {
            if (message.size <= CHUNK_SIZE) {
                // رسالة صغيرة - إرسال مباشر
                return@withContext sendSingleAdvertisement(message)
            } else {
                // رسالة كبيرة - تقسيم لقطع
                return@withContext sendChunkedAdvertisements(message)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Advertising relay failed", e)
            return@withContext false
        }
    }

    /**
     * إرسال إعلان واحد
     */
    private suspend fun sendSingleAdvertisement(data: ByteArray): Boolean {
        val advertiseData = AdvertiseData.Builder()
            .addServiceData(
                ParcelUuid(MeshNetworkManager.GENERIC_RELAY_SERVICE),
                data
            )
            .build()

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTimeout(3000)
            .build()

        val advertiseDeferred = CompletableDeferred<Boolean>()
        
        val advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                advertiseDeferred.complete(true)
            }
            override fun onStartFailure(errorCode: Int) {
                advertiseDeferred.complete(false)
            }
        }

        bluetoothLeAdvertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
        
        val result = withTimeoutOrNull(4000) {
            advertiseDeferred.await()
        } ?: false

        bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
        return result
    }

    /**
     * إرسال إعلانات مقسمة
     */
    private suspend fun sendChunkedAdvertisements(data: ByteArray): Boolean {
        val chunks = data.toList().chunked(CHUNK_SIZE)
        
        for (i in chunks.indices) {
            val chunk = chunks[i].toByteArray()
            val chunkData = createChunkData(chunk, i, chunks.size)
            
            if (!sendSingleAdvertisement(chunkData)) {
                return false
            }
            
            delay(200) // تأخير بين القطع
        }
        
        return true
    }

    /**
     * إنشاء بيانات قطعة مع معلومات التسلسل
     */
    private fun createChunkData(chunk: ByteArray, index: Int, total: Int): ByteArray {
        val header = byteArrayOf(
            0xBC.toByte(), // BitChat identifier
            index.toByte(),
            total.toByte()
        )
        return header + chunk
    }

    /**
     * تشفير الرسالة كبيانات "عادية"
     */
    private fun encodeMessageAsNormalData(message: ByteArray): ByteArray {
        // إضافة header يبدو كبيانات sensor عادية
        val fakeHeader = byteArrayOf(
            0x01, // نوع البيانات "وهمي"
            0x02, // إصدار البروتوكول "وهمي"
            message.size.toByte()
        )
        return fakeHeader + message
    }

    /**
     * تشفير الرسالة في اسم الجهاز
     */
    private fun encodeMessageInDeviceName(message: ByteArray): String {
        // تحويل البيانات لـ Base64 مختصر
        val encoded = android.util.Base64.encodeToString(message, android.util.Base64.NO_WRAP)
            .take(20) // حد أسماء البلوتوث
        return "BC_$encoded" // BitChat prefix
    }

    /**
     * تنظيف الموارد
     */
    fun destroy() {
        coroutineScope.cancel()
    }
}

/**
 * استراتيجيات التمرير المختلفة
 */
sealed class RelayStrategy {
    data class GattService(val serviceUuid: UUID) : RelayStrategy()
    object DeviceNameEncoding : RelayStrategy()
    object ManufacturerDataHijack : RelayStrategy()
    object ServiceDataHijack : RelayStrategy()
    object AdvertisingRelay : RelayStrategy()
}

/**
 * جهاز قادر على التمرير
 */
data class RelayCapableDevice(
    val deviceId: String,
    val bluetoothAddress: String,
    val deviceName: String,
    val signalStrength: Int,
    val strategies: List<RelayStrategy>,
    val reliabilityScore: Float
)

/**
 * قدرات الجهاز
 */
data class DeviceCapabilities(
    val hasGattServer: Boolean = false,
    val supportedServices: List<UUID> = emptyList(),
    val canModifyAdvertising: Boolean = false,
    val maxDataSize: Int = 20
)