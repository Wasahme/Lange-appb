package com.bitchat.app.mesh

import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import com.bitchat.app.data.entities.Device
import com.bitchat.app.data.entities.Message
import com.bitchat.app.data.entities.MeshRoute
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * مدير الشبكة الشبكية المتقدم
 * يمكنه استخدام أي أجهزة بلوتوث كنقاط تمرير حتى لو لم يكن لديها التطبيق
 */
@Singleton
class MeshNetworkManager @Inject constructor(
    private val context: Context
) {
    companion object {
        private const val TAG = "MeshNetworkManager"
        
        // UUIDs مختلفة لأنواع البيانات
        val BITCHAT_MESSAGE_SERVICE = UUID.fromString("12345678-1234-5678-9012-123456789abc")
        val BITCHAT_DISCOVERY_SERVICE = UUID.fromString("12345678-1234-5678-9012-123456789abd")
        val GENERIC_RELAY_SERVICE = UUID.fromString("12345678-1234-5678-9012-123456789abe")
        
        // خصائص مختلفة للبيانات
        val MESSAGE_CHARACTERISTIC = UUID.fromString("87654321-4321-8765-2109-cba987654321")
        val ROUTE_CHARACTERISTIC = UUID.fromString("87654321-4321-8765-2109-cba987654322")
        val RELAY_CHARACTERISTIC = UUID.fromString("87654321-4321-8765-2109-cba987654323")
        
        const val MAX_HOPS = 10
        const val MESSAGE_TTL = 30 * 60 * 1000L // 30 دقيقة
        const val RELAY_TIMEOUT = 5000L // 5 ثواني
    }

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner
    private val bluetoothLeAdvertiser = bluetoothAdapter?.bluetoothLeAdvertiser

    private val _meshStatus = MutableStateFlow(MeshStatus.DISCONNECTED)
    val meshStatus: StateFlow<MeshStatus> = _meshStatus

    private val _availableRelays = MutableStateFlow<List<RelayNode>>(emptyList())
    val availableRelays: StateFlow<List<RelayNode>> = _availableRelays

    private val _routingTable = MutableStateFlow<Map<String, List<MeshRoute>>>(emptyMap())
    val routingTable: StateFlow<Map<String, List<MeshRoute>>> = _routingTable

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // تخزين مؤقت للرسائل المرسلة لتجنب التكرار
    private val messageCache = mutableSetOf<String>()
    
    // قائمة الأجهزة النشطة التي يمكن استخدامها كـ relays
    private val activeRelays = mutableMapOf<String, RelayNode>()
    
    // قائمة انتظار الرسائل
    private val pendingMessages = mutableListOf<PendingMessage>()

    private var gattServer: BluetoothGattServer? = null
    private var scanCallback: ScanCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null

    init {
        setupGattServer()
        startMeshDiscovery()
    }

    /**
     * إعداد GATT Server لاستقبال الرسائل وتمريرها
     */
    private fun setupGattServer() {
        val gattServerCallback = object : BluetoothGattServerCallback() {
            override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
                super.onConnectionStateChange(device, status, newState)
                when (newState) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        Log.d(TAG, "Device connected: ${device?.address}")
                        device?.let { handleDeviceConnected(it) }
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        Log.d(TAG, "Device disconnected: ${device?.address}")
                        device?.let { handleDeviceDisconnected(it) }
                    }
                }
            }

            override fun onCharacteristicWriteRequest(
                device: BluetoothDevice?,
                requestId: Int,
                characteristic: BluetoothGattCharacteristic?,
                preparedWrite: Boolean,
                responseNeeded: Boolean,
                offset: Int,
                value: ByteArray?
            ) {
                super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value)
                
                if (responseNeeded) {
                    gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                }

                value?.let { data ->
                    when (characteristic?.uuid) {
                        MESSAGE_CHARACTERISTIC -> {
                            handleIncomingMessage(device, data)
                        }
                        ROUTE_CHARACTERISTIC -> {
                            handleRouteUpdate(device, data)
                        }
                        RELAY_CHARACTERISTIC -> {
                            handleRelayRequest(device, data)
                        }
                    }
                }
            }

            override fun onCharacteristicReadRequest(
                device: BluetoothDevice?,
                requestId: Int,
                offset: Int,
                characteristic: BluetoothGattCharacteristic?
            ) {
                super.onCharacteristicReadRequest(device, requestId, offset, characteristic)
                
                when (characteristic?.uuid) {
                    ROUTE_CHARACTERISTIC -> {
                        val routeData = getLocalRoutingData()
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, routeData)
                    }
                    else -> {
                        gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
                    }
                }
            }
        }

        try {
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            
            // إضافة خدمات مختلفة
            addMessageService()
            addDiscoveryService()
            addGenericRelayService()
            
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception setting up GATT server", e)
        }
    }

    /**
     * إضافة خدمة الرسائل
     */
    private fun addMessageService() {
        val messageService = BluetoothGattService(
            BITCHAT_MESSAGE_SERVICE,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val messageCharacteristic = BluetoothGattCharacteristic(
            MESSAGE_CHARACTERISTIC,
            BluetoothGattCharacteristic.PROPERTY_READ or 
            BluetoothGattCharacteristic.PROPERTY_WRITE or
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or 
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        messageService.addCharacteristic(messageCharacteristic)
        gattServer?.addService(messageService)
    }

    /**
     * إضافة خدمة الاكتشاف
     */
    private fun addDiscoveryService() {
        val discoveryService = BluetoothGattService(
            BITCHAT_DISCOVERY_SERVICE,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val routeCharacteristic = BluetoothGattCharacteristic(
            ROUTE_CHARACTERISTIC,
            BluetoothGattCharacteristic.PROPERTY_READ or 
            BluetoothGattCharacteristic.PROPERTY_WRITE or
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ or 
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        discoveryService.addCharacteristic(routeCharacteristic)
        gattServer?.addService(discoveryService)
    }

    /**
     * إضافة خدمة التمرير العامة للأجهزة التي لا تحتوي على التطبيق
     */
    private fun addGenericRelayService() {
        val relayService = BluetoothGattService(
            GENERIC_RELAY_SERVICE,
            BluetoothGattService.SERVICE_TYPE_PRIMARY
        )

        val relayCharacteristic = BluetoothGattCharacteristic(
            RELAY_CHARACTERISTIC,
            BluetoothGattCharacteristic.PROPERTY_READ or 
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or 
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )

        relayService.addCharacteristic(relayCharacteristic)
        gattServer?.addService(relayService)
    }

    /**
     * بدء اكتشاف الشبكة الشبكية
     */
    private fun startMeshDiscovery() {
        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                super.onScanResult(callbackType, result)
                processDiscoveredDevice(result)
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                super.onBatchScanResults(results)
                results.forEach { processDiscoveredDevice(it) }
            }
        }

        // البحث عن جميع أجهزة البلوتوث
        val scanSettings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
            .build()

        try {
            bluetoothLeScanner?.startScan(emptyList(), scanSettings, scanCallback)
            _meshStatus.value = MeshStatus.SCANNING
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting scan", e)
        }

        // بدء الإعلان كنقطة تمرير
        startAdvertising()
    }

    /**
     * معالجة الأجهزة المكتشفة
     */
    private fun processDiscoveredDevice(result: ScanResult) {
        val device = result.device
        val rssi = result.rssi
        val scanRecord = result.scanRecord

        try {
            val deviceId = device.address.replace(":", "")
            
            // فحص إذا كان الجهاز يدعم BitChat
            val hasBitChat = scanRecord?.serviceUuids?.any { 
                it.uuid == BITCHAT_MESSAGE_SERVICE || 
                it.uuid == BITCHAT_DISCOVERY_SERVICE 
            } ?: false

            // حتى الأجهزة التي لا تدعم BitChat يمكن استخدامها كـ relays
            val canRelay = hasBitChat || canDeviceRelay(scanRecord)

            if (canRelay) {
                val relayNode = RelayNode(
                    deviceId = deviceId,
                    bluetoothAddress = device.address,
                    deviceName = device.name ?: "Unknown Device",
                    hasBitChat = hasBitChat,
                    signalStrength = rssi,
                    lastSeen = System.currentTimeMillis(),
                    hopCount = if (hasBitChat) 1 else 2, // أجهزة غير BitChat تحتاج hop إضافي
                    relayCapability = if (hasBitChat) RelayCapability.FULL else RelayCapability.BASIC
                )

                activeRelays[deviceId] = relayNode
                updateAvailableRelays()

                // محاولة الاتصال إذا كان لدينا رسائل معلقة
                if (pendingMessages.isNotEmpty()) {
                    attemptMessageRelay(relayNode)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error processing discovered device", e)
        }
    }

    /**
     * فحص إذا كان الجهاز يمكنه العمل كـ relay حتى بدون التطبيق
     */
    private fun canDeviceRelay(scanRecord: ScanRecord?): Boolean {
        // فحص الخدمات المتاحة
        val services = scanRecord?.serviceUuids
        
        // البحث عن خدمات بلوتوث عامة يمكن استخدامها
        val relayCapableServices = setOf(
            // خدمات GATT عامة
            UUID.fromString("00001800-0000-1000-8000-00805f9b34fb"), // Generic Access
            UUID.fromString("00001801-0000-1000-8000-00805f9b34fb"), // Generic Attribute
            UUID.fromString("0000180a-0000-1000-8000-00805f9b34fb"), // Device Information
            // خدمات مخصصة شائعة
            UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e"), // Nordic UART
        )

        return services?.any { serviceUuid ->
            relayCapableServices.contains(serviceUuid.uuid)
        } ?: false
    }

    /**
     * بدء الإعلان كنقطة تمرير
     */
    private fun startAdvertising() {
        advertiseCallback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                super.onStartSuccess(settingsInEffect)
                Log.d(TAG, "Started advertising as mesh relay")
                _meshStatus.value = MeshStatus.ACTIVE
            }

            override fun onStartFailure(errorCode: Int) {
                super.onStartFailure(errorCode)
                Log.e(TAG, "Failed to start advertising: $errorCode")
            }
        }

        val advertiseSettings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(true)
            .build()

        val advertiseData = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(true)
            .addServiceUuid(ParcelUuid(BITCHAT_MESSAGE_SERVICE))
            .addServiceUuid(ParcelUuid(BITCHAT_DISCOVERY_SERVICE))
            .addServiceUuid(ParcelUuid(GENERIC_RELAY_SERVICE))
            .build()

        try {
            bluetoothLeAdvertiser?.startAdvertising(advertiseSettings, advertiseData, advertiseCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting advertising", e)
        }
    }

    /**
     * إرسال رسالة عبر الشبكة الشبكية
     */
    suspend fun sendMessage(message: Message, targetDeviceId: String): Boolean {
        val messagePacket = MeshMessagePacket(
            messageId = message.messageId,
            sourceDeviceId = getCurrentDeviceId(),
            targetDeviceId = targetDeviceId,
            content = Json.encodeToString(message),
            hopCount = 0,
            ttl = System.currentTimeMillis() + MESSAGE_TTL,
            routePath = mutableListOf(getCurrentDeviceId())
        )

        return relayMessage(messagePacket)
    }

    /**
     * تمرير الرسالة عبر الشبكة
     */
    private suspend fun relayMessage(packet: MeshMessagePacket): Boolean = withContext(Dispatchers.IO) {
        // تجنب إعادة إرسال نفس الرسالة
        if (messageCache.contains(packet.messageId)) {
            return@withContext false
        }

        messageCache.add(packet.messageId)

        // فحص TTL
        if (packet.ttl < System.currentTimeMillis() || packet.hopCount >= MAX_HOPS) {
            return@withContext false
        }

        // البحث عن أفضل relay
        val bestRelay = findBestRelay(packet.targetDeviceId, packet.routePath)
        
        if (bestRelay != null) {
            return@withContext sendToRelay(packet, bestRelay)
        } else {
            // إضافة للقائمة المعلقة
            pendingMessages.add(PendingMessage(packet, System.currentTimeMillis()))
            return@withContext false
        }
    }

    /**
     * البحث عن أفضل relay للهدف
     */
    private fun findBestRelay(targetDeviceId: String, excludePath: List<String>): RelayNode? {
        return activeRelays.values
            .filter { relay ->
                // استبعاد الأجهزة في المسار لتجنب الحلقات
                !excludePath.contains(relay.deviceId) &&
                // التأكد من أن الجهاز متاح
                (System.currentTimeMillis() - relay.lastSeen) < 60000 &&
                // فحص قوة الإشارة
                relay.signalStrength > -80
            }
            .sortedWith(compareBy<RelayNode> { relay ->
                // أولوية للأجهزة التي لديها BitChat
                if (relay.hasBitChat) 0 else 1
            }.thenBy { relay ->
                // ثم حسب عدد الـ hops
                relay.hopCount
            }.thenByDescending { relay ->
                // ثم حسب قوة الإشارة
                relay.signalStrength
            })
            .firstOrNull()
    }

    /**
     * إرسال الرسالة إلى relay محدد
     */
    private suspend fun sendToRelay(packet: MeshMessagePacket, relay: RelayNode): Boolean = withContext(Dispatchers.IO) {
        try {
            // تحديث معلومات الحزمة
            val updatedPacket = packet.copy(
                hopCount = packet.hopCount + 1,
                routePath = packet.routePath + relay.deviceId
            )

            val packetData = Json.encodeToString(updatedPacket).toByteArray()

            // اختيار طريقة الإرسال حسب نوع الجهاز
            return@withContext if (relay.hasBitChat) {
                sendToBitChatDevice(relay, packetData)
            } else {
                sendToGenericDevice(relay, packetData)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send to relay ${relay.deviceId}", e)
            return@withContext false
        }
    }

    /**
     * إرسال لجهاز يحتوي على BitChat
     */
    private suspend fun sendToBitChatDevice(relay: RelayNode, data: ByteArray): Boolean {
        // اتصال GATT مباشر
        return connectAndSend(relay.bluetoothAddress, MESSAGE_CHARACTERISTIC, data)
    }

    /**
     * إرسال لجهاز عام (بدون BitChat)
     */
    private suspend fun sendToGenericDevice(relay: RelayNode, data: ByteArray): Boolean {
        // محاولة استخدام خدمات بلوتوث عامة
        return connectAndSend(relay.bluetoothAddress, RELAY_CHARACTERISTIC, data) ||
               // أو استخدام UART إذا متاح
               connectAndSendViaUART(relay.bluetoothAddress, data) ||
               // أو تقسيم البيانات واستخدام advertising
               sendViaAdvertising(relay, data)
    }

    /**
     * الاتصال وإرسال البيانات
     */
    private suspend fun connectAndSend(
        bluetoothAddress: String, 
        characteristicUuid: UUID, 
        data: ByteArray
    ): Boolean = withContext(Dispatchers.IO) {
        
        var gatt: BluetoothGatt? = null
        var success = false

        try {
            val device = bluetoothAdapter.getRemoteDevice(bluetoothAddress)
            val connectionDeferred = CompletableDeferred<Boolean>()

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
                        val characteristic = gatt?.getService(BITCHAT_MESSAGE_SERVICE)
                            ?.getCharacteristic(characteristicUuid)
                            ?: gatt?.getService(GENERIC_RELAY_SERVICE)
                                ?.getCharacteristic(characteristicUuid)

                        if (characteristic != null) {
                            characteristic.value = data
                            val writeSuccess = gatt?.writeCharacteristic(characteristic) ?: false
                            connectionDeferred.complete(writeSuccess)
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
            
            // انتظار لمدة أقصاها 5 ثواني
            success = withTimeoutOrNull(RELAY_TIMEOUT) {
                connectionDeferred.await()
            } ?: false

        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to device $bluetoothAddress", e)
        } finally {
            try {
                gatt?.disconnect()
                gatt?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Error closing GATT connection", e)
            }
        }

        return@withContext success
    }

    /**
     * إرسال عبر Nordic UART Service
     */
    private suspend fun connectAndSendViaUART(bluetoothAddress: String, data: ByteArray): Boolean {
        val uartServiceUuid = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val uartTxCharUuid = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        
        // نفس منطق الاتصال ولكن باستخدام UART
        return connectAndSend(bluetoothAddress, uartTxCharUuid, data)
    }

    /**
     * إرسال عبر Advertising (للبيانات الصغيرة)
     */
    private fun sendViaAdvertising(relay: RelayNode, data: ByteArray): Boolean {
        if (data.size > 20) return false // حد الـ advertising

        try {
            val advertiseData = AdvertiseData.Builder()
                .addServiceData(ParcelUuid(GENERIC_RELAY_SERVICE), data)
                .build()

            val advertiseSettings = AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTimeout(3000) // 3 ثواني
                .build()

            val callback = object : AdvertiseCallback() {
                override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                    Log.d(TAG, "Successfully sent via advertising to ${relay.deviceId}")
                }
            }

            bluetoothLeAdvertiser?.startAdvertising(advertiseSettings, advertiseData, callback)
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to send via advertising", e)
            return false
        }
    }

    /**
     * معالجة الرسالة الواردة
     */
    private fun handleIncomingMessage(device: BluetoothDevice?, data: ByteArray) {
        try {
            val packetJson = String(data)
            val packet = Json.decodeFromString<MeshMessagePacket>(packetJson)

            // فحص إذا كانت الرسالة موجهة لنا
            if (packet.targetDeviceId == getCurrentDeviceId()) {
                // معالجة الرسالة النهائية
                val message = Json.decodeFromString<Message>(packet.content)
                onMessageReceived(message)
            } else {
                // تمرير الرسالة للهدف التالي
                coroutineScope.launch {
                    relayMessage(packet)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming message", e)
        }
    }

    /**
     * معالجة تحديث المسار
     */
    private fun handleRouteUpdate(device: BluetoothDevice?, data: ByteArray) {
        try {
            val routeData = Json.decodeFromString<List<MeshRoute>>(String(data))
            updateRoutingTable(device?.address?.replace(":", "") ?: "", routeData)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling route update", e)
        }
    }

    /**
     * معالجة طلب التمرير
     */
    private fun handleRelayRequest(device: BluetoothDevice?, data: ByteArray) {
        try {
            val packetJson = String(data)
            val packet = Json.decodeFromString<MeshMessagePacket>(packetJson)

            // تمرير الرسالة إذا لم تكن موجهة لنا
            if (packet.targetDeviceId != getCurrentDeviceId()) {
                coroutineScope.launch {
                    relayMessage(packet)
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling relay request", e)
        }
    }

    /**
     * معالجة اتصال جهاز جديد
     */
    private fun handleDeviceConnected(device: BluetoothDevice) {
        val deviceId = device.address.replace(":", "")
        Log.d(TAG, "Device connected: $deviceId")
        
        // إضافة كـ relay محتمل
        val relayNode = RelayNode(
            deviceId = deviceId,
            bluetoothAddress = device.address,
            deviceName = device.name ?: "Connected Device",
            hasBitChat = false, // سنفحص لاحقاً
            signalStrength = -50, // افتراضي للأجهزة المتصلة
            lastSeen = System.currentTimeMillis(),
            hopCount = 1,
            relayCapability = RelayCapability.BASIC
        )

        activeRelays[deviceId] = relayNode
        updateAvailableRelays()
    }

    /**
     * معالجة انقطاع اتصال جهاز
     */
    private fun handleDeviceDisconnected(device: BluetoothDevice) {
        val deviceId = device.address.replace(":", "")
        activeRelays.remove(deviceId)
        updateAvailableRelays()
    }

    /**
     * تحديث قائمة الـ relays المتاحة
     */
    private fun updateAvailableRelays() {
        _availableRelays.value = activeRelays.values.toList()
    }

    /**
     * محاولة تمرير الرسائل المعلقة
     */
    private fun attemptMessageRelay(relay: RelayNode) {
        coroutineScope.launch {
            val iterator = pendingMessages.iterator()
            while (iterator.hasNext()) {
                val pendingMessage = iterator.next()
                
                // فحص انتهاء الصلاحية
                if (pendingMessage.timestamp + MESSAGE_TTL < System.currentTimeMillis()) {
                    iterator.remove()
                    continue
                }

                // محاولة الإرسال
                if (sendToRelay(pendingMessage.packet, relay)) {
                    iterator.remove()
                }
            }
        }
    }

    /**
     * الحصول على بيانات التوجيه المحلية
     */
    private fun getLocalRoutingData(): ByteArray {
        val routes = _routingTable.value[getCurrentDeviceId()] ?: emptyList()
        return Json.encodeToString(routes).toByteArray()
    }

    /**
     * تحديث جدول التوجيه
     */
    private fun updateRoutingTable(deviceId: String, routes: List<MeshRoute>) {
        val currentTable = _routingTable.value.toMutableMap()
        currentTable[deviceId] = routes
        _routingTable.value = currentTable
    }

    /**
     * الحصول على معرف الجهاز الحالي
     */
    private fun getCurrentDeviceId(): String {
        return bluetoothAdapter?.address?.replace(":", "") ?: "unknown"
    }

    /**
     * دالة لاستقبال الرسائل
     */
    private fun onMessageReceived(message: Message) {
        Log.d(TAG, "Message received: ${message.messageId}")
        // هنا يمكن إضافة الرسالة لقاعدة البيانات أو إشعار الواجهة
    }

    /**
     * تنظيف الموارد
     */
    fun destroy() {
        try {
            bluetoothLeScanner?.stopScan(scanCallback)
            bluetoothLeAdvertiser?.stopAdvertising(advertiseCallback)
            gattServer?.close()
            coroutineScope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup", e)
        }
    }
}

/**
 * حالة الشبكة الشبكية
 */
enum class MeshStatus {
    DISCONNECTED,
    SCANNING,
    ACTIVE,
    ERROR
}

/**
 * عقدة تمرير في الشبكة
 */
data class RelayNode(
    val deviceId: String,
    val bluetoothAddress: String,
    val deviceName: String,
    val hasBitChat: Boolean,
    val signalStrength: Int,
    val lastSeen: Long,
    val hopCount: Int,
    val relayCapability: RelayCapability
)

/**
 * قدرة الجهاز على التمرير
 */
enum class RelayCapability {
    NONE,       // لا يمكنه التمرير
    BASIC,      // تمرير أساسي
    FULL        // تمرير كامل مع BitChat
}

/**
 * حزمة رسالة الشبكة الشبكية
 */
@kotlinx.serialization.Serializable
data class MeshMessagePacket(
    val messageId: String,
    val sourceDeviceId: String,
    val targetDeviceId: String,
    val content: String,
    val hopCount: Int,
    val ttl: Long,
    val routePath: List<String>
)

/**
 * رسالة معلقة
 */
data class PendingMessage(
    val packet: MeshMessagePacket,
    val timestamp: Long
)