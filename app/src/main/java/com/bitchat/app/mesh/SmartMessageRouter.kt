package com.bitchat.app.mesh

import android.content.Context
import android.util.Log
import com.bitchat.app.data.entities.Message
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * موجه الرسائل الذكي
 * يختار أفضل مسار للرسائل عبر جميع الأجهزة المتاحة
 */
@Singleton
class SmartMessageRouter @Inject constructor(
    private val context: Context,
    private val meshNetworkManager: MeshNetworkManager,
    private val universalRelay: UniversalBluetoothRelay
) {
    companion object {
        private const val TAG = "SmartMessageRouter"
        private const val MAX_ROUTING_ATTEMPTS = 5
        private const val ROUTE_DISCOVERY_TIMEOUT = 15000L // 15 ثانية
        private const val MESSAGE_RETRY_DELAY = 2000L // ثانيتان
    }

    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // حالة الموجه
    private val _routingStatus = MutableStateFlow(RoutingStatus.IDLE)
    val routingStatus: StateFlow<RoutingStatus> = _routingStatus.asStateFlow()

    // قائمة المسارات المتاحة
    private val _availableRoutes = MutableStateFlow<List<RoutingPath>>(emptyList())
    val availableRoutes: StateFlow<List<RoutingPath>> = _availableRoutes.asStateFlow()

    // إحصائيات التوجيه
    private val _routingStats = MutableStateFlow(RoutingStatistics())
    val routingStats: StateFlow<RoutingStatistics> = _routingStats.asStateFlow()

    // قاموس المسارات المكتشفة حسب الهدف
    private val discoveredRoutes = mutableMapOf<String, List<RoutingPath>>()
    
    // طابور الرسائل المعلقة
    private val pendingMessages = mutableListOf<PendingRoutedMessage>()

    init {
        startRouteDiscovery()
        startMessageProcessor()
    }

    /**
     * إرسال رسالة عبر أفضل مسار متاح
     */
    suspend fun routeMessage(message: Message, targetDeviceId: String): RoutingResult {
        return withContext(Dispatchers.IO) {
            _routingStatus.value = RoutingStatus.ROUTING

            try {
                // البحث عن المسارات المتاحة للهدف
                val routes = findRoutesToTarget(targetDeviceId)
                
                if (routes.isEmpty()) {
                    // لا توجد مسارات - محاولة اكتشاف جديد
                    val discoveredRoutes = discoverRoutesToTarget(targetDeviceId)
                    if (discoveredRoutes.isEmpty()) {
                        return@withContext RoutingResult.NO_ROUTE_FOUND
                    }
                }

                // ترتيب المسارات حسب الأولوية
                val sortedRoutes = routes.sortedBy { it.priority }
                
                // محاولة الإرسال عبر كل مسار
                for (route in sortedRoutes) {
                    val success = attemptSendViaRoute(message, route)
                    if (success) {
                        updateRoutingStats(true, route)
                        _routingStatus.value = RoutingStatus.IDLE
                        return@withContext RoutingResult.SUCCESS
                    }
                }

                // فشل جميع المسارات - إضافة للطابور
                addToPendingQueue(message, targetDeviceId)
                _routingStatus.value = RoutingStatus.IDLE
                return@withContext RoutingResult.QUEUED

            } catch (e: Exception) {
                Log.e(TAG, "Error routing message", e)
                _routingStatus.value = RoutingStatus.IDLE
                return@withContext RoutingResult.ERROR
            }
        }
    }

    /**
     * البحث عن المسارات المتاحة لهدف معين
     */
    private suspend fun findRoutesToTarget(targetDeviceId: String): List<RoutingPath> {
        val routes = mutableListOf<RoutingPath>()

        // 1. المسارات المباشرة عبر BitChat
        val directBitChatRoutes = findDirectBitChatRoutes(targetDeviceId)
        routes.addAll(directBitChatRoutes)

        // 2. المسارات عبر الشبكة الشبكية
        val meshRoutes = findMeshNetworkRoutes(targetDeviceId)
        routes.addAll(meshRoutes)

        // 3. المسارات عبر الأجهزة العامة
        val universalRoutes = findUniversalRelayRoutes(targetDeviceId)
        routes.addAll(universalRoutes)

        // 4. المسارات المختلطة
        val hybridRoutes = findHybridRoutes(targetDeviceId)
        routes.addAll(hybridRoutes)

        return routes.distinctBy { "${it.routeType}_${it.hops.joinToString("_")}" }
    }

    /**
     * البحث عن المسارات المباشرة عبر BitChat
     */
    private suspend fun findDirectBitChatRoutes(targetDeviceId: String): List<RoutingPath> {
        val routes = mutableListOf<RoutingPath>()
        
        // الأجهزة التي تحتوي على BitChat
        meshNetworkManager.availableRelays.value
            .filter { it.hasBitChat }
            .forEach { relay ->
                routes.add(
                    RoutingPath(
                        routeType = RouteType.DIRECT_BITCHAT,
                        hops = listOf(relay.deviceId),
                        reliability = calculateRouteReliability(listOf(relay)),
                        latency = estimateLatency(listOf(relay)),
                        priority = 1
                    )
                )
            }

        return routes
    }

    /**
     * البحث عن المسارات عبر الشبكة الشبكية
     */
    private suspend fun findMeshNetworkRoutes(targetDeviceId: String): List<RoutingPath> {
        val routes = mutableListOf<RoutingPath>()
        val meshRoutes = meshNetworkManager.routingTable.value

        meshRoutes[targetDeviceId]?.forEach { meshRoute ->
            if (meshRoute.isActive) {
                routes.add(
                    RoutingPath(
                        routeType = RouteType.MESH_NETWORK,
                        hops = listOf(meshRoute.nextHopDeviceId, meshRoute.destinationDeviceId),
                        reliability = 0.8f - (meshRoute.hopCount * 0.1f),
                        latency = meshRoute.hopCount * 500L,
                        priority = 2
                    )
                )
            }
        }

        return routes
    }

    /**
     * البحث عن المسارات عبر الأجهزة العامة
     */
    private suspend fun findUniversalRelayRoutes(targetDeviceId: String): List<RoutingPath> {
        val routes = mutableListOf<RoutingPath>()
        
        // الحصول على الأجهزة القادرة على التمرير
        val relayCapableDevices = universalRelay.discoverRelayCapabilities()
        
        relayCapableDevices
            .filter { it.reliabilityScore > 5.0f }
            .forEach { device ->
                routes.add(
                    RoutingPath(
                        routeType = RouteType.UNIVERSAL_RELAY,
                        hops = listOf(device.deviceId),
                        reliability = device.reliabilityScore / 10.0f,
                        latency = when {
                            device.strategies.any { it is RelayStrategy.GattService } -> 1000L
                            device.strategies.any { it is RelayStrategy.ServiceDataHijack } -> 2000L
                            else -> 3000L
                        },
                        priority = 3
                    )
                )
            }

        return routes
    }

    /**
     * البحث عن المسارات المختلطة
     */
    private suspend fun findHybridRoutes(targetDeviceId: String): List<RoutingPath> {
        val routes = mutableListOf<RoutingPath>()
        
        // دمج BitChat devices مع Universal relays
        val bitChatDevices = meshNetworkManager.availableRelays.value.filter { it.hasBitChat }
        val universalDevices = universalRelay.discoverRelayCapabilities()

        // إنشاء مسارات متعددة الخطوات
        bitChatDevices.forEach { bitChatDevice ->
            universalDevices
                .filter { it.deviceId != bitChatDevice.deviceId }
                .forEach { universalDevice ->
                    routes.add(
                        RoutingPath(
                            routeType = RouteType.HYBRID,
                            hops = listOf(bitChatDevice.deviceId, universalDevice.deviceId),
                            reliability = (bitChatDevice.signalStrength + universalDevice.signalStrength) / 200.0f,
                            latency = 2500L,
                            priority = 4
                        )
                    )
                }
        }

        return routes
    }

    /**
     * اكتشاف مسارات جديدة لهدف معين
     */
    private suspend fun discoverRoutesToTarget(targetDeviceId: String): List<RoutingPath> {
        return withContext(Dispatchers.IO) {
            val discoveredRoutes = mutableListOf<RoutingPath>()

            try {
                // إرسال broadcast للبحث عن الهدف
                val discoveryPacket = RouteDiscoveryPacket(
                    searchId = UUID.randomUUID().toString(),
                    sourceDeviceId = getCurrentDeviceId(),
                    targetDeviceId = targetDeviceId,
                    hopCount = 0,
                    timestamp = System.currentTimeMillis()
                )

                // بث الطلب عبر جميع الأجهزة المتاحة
                broadcastDiscoveryRequest(discoveryPacket)

                // انتظار الردود لمدة محددة
                delay(ROUTE_DISCOVERY_TIMEOUT)

                // جمع النتائج
                val routes = collectDiscoveryResults(discoveryPacket.searchId)
                discoveredRoutes.addAll(routes)

                // حفظ المسارات المكتشفة
                this@SmartMessageRouter.discoveredRoutes[targetDeviceId] = discoveredRoutes

            } catch (e: Exception) {
                Log.e(TAG, "Route discovery failed", e)
            }

            return@withContext discoveredRoutes
        }
    }

    /**
     * محاولة الإرسال عبر مسار محدد
     */
    private suspend fun attemptSendViaRoute(message: Message, route: RoutingPath): Boolean {
        return try {
            when (route.routeType) {
                RouteType.DIRECT_BITCHAT -> {
                    sendViaDirectBitChat(message, route)
                }
                RouteType.MESH_NETWORK -> {
                    sendViaMeshNetwork(message, route)
                }
                RouteType.UNIVERSAL_RELAY -> {
                    sendViaUniversalRelay(message, route)
                }
                RouteType.HYBRID -> {
                    sendViaHybridRoute(message, route)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send via route ${route.routeType}", e)
            false
        }
    }

    /**
     * الإرسال المباشر عبر BitChat
     */
    private suspend fun sendViaDirectBitChat(message: Message, route: RoutingPath): Boolean {
        val targetDeviceId = route.hops.last()
        return meshNetworkManager.sendMessage(message, targetDeviceId)
    }

    /**
     * الإرسال عبر الشبكة الشبكية
     */
    private suspend fun sendViaMeshNetwork(message: Message, route: RoutingPath): Boolean {
        val targetDeviceId = route.hops.last()
        return meshNetworkManager.sendMessage(message, targetDeviceId)
    }

    /**
     * الإرسال عبر التمرير العام
     */
    private suspend fun sendViaUniversalRelay(message: Message, route: RoutingPath): Boolean {
        val relayDeviceId = route.hops.first()
        val relayDevices = universalRelay.discoverRelayCapabilities()
        val relayDevice = relayDevices.find { it.deviceId == relayDeviceId }
            ?: return false

        val messageData = Json.encodeToString(message).toByteArray()
        return universalRelay.relayMessageThroughDevice(
            messageData, 
            relayDevice, 
            route.hops.last()
        )
    }

    /**
     * الإرسال عبر المسار المختلط
     */
    private suspend fun sendViaHybridRoute(message: Message, route: RoutingPath): Boolean {
        // إرسال عبر الخطوة الأولى (BitChat) ثم التمرير العام
        val firstHop = route.hops[0]
        val secondHop = route.hops[1]

        // أولاً عبر BitChat
        val firstStepSuccess = meshNetworkManager.sendMessage(message, firstHop)
        if (!firstStepSuccess) return false

        // ثم عبر Universal Relay
        delay(1000) // انتظار وصول الرسالة للخطوة الأولى
        
        val relayDevices = universalRelay.discoverRelayCapabilities()
        val relayDevice = relayDevices.find { it.deviceId == secondHop }
            ?: return false

        val messageData = Json.encodeToString(message).toByteArray()
        return universalRelay.relayMessageThroughDevice(
            messageData, 
            relayDevice, 
            route.hops.last()
        )
    }

    /**
     * بث طلب اكتشاف المسار
     */
    private suspend fun broadcastDiscoveryRequest(discoveryPacket: RouteDiscoveryPacket) {
        // بث عبر BitChat devices
        meshNetworkManager.availableRelays.value.forEach { relay ->
            // إرسال طلب الاكتشاف
            // هذا يتطلب تطوير بروتوكول اكتشاف خاص
        }

        // بث عبر Universal devices
        val universalDevices = universalRelay.discoverRelayCapabilities()
        universalDevices.forEach { device ->
            // إرسال طلب الاكتشاف عبر الأجهزة العامة
        }
    }

    /**
     * جمع نتائج الاكتشاف
     */
    private suspend fun collectDiscoveryResults(searchId: String): List<RoutingPath> {
        // مؤقتاً نعيد قائمة فارغة
        // في التطبيق الفعلي سيتم جمع الردود من الأجهزة
        return emptyList()
    }

    /**
     * حساب موثوقية المسار
     */
    private fun calculateRouteReliability(relays: List<RelayNode>): Float {
        if (relays.isEmpty()) return 0f
        
        var totalReliability = 1.0f
        relays.forEach { relay ->
            val deviceReliability = when {
                relay.signalStrength > -50 -> 0.95f
                relay.signalStrength > -70 -> 0.85f
                relay.signalStrength > -80 -> 0.75f
                else -> 0.60f
            }
            totalReliability *= deviceReliability
        }
        
        return totalReliability
    }

    /**
     * تقدير زمن الاستجابة
     */
    private fun estimateLatency(relays: List<RelayNode>): Long {
        val baseLatency = 200L // زمن أساسي
        val hopLatency = relays.size * 300L // زمن إضافي لكل hop
        val signalPenalty = relays.sumOf { relay ->
            when {
                relay.signalStrength > -50 -> 0L
                relay.signalStrength > -70 -> 100L
                relay.signalStrength > -80 -> 300L
                else -> 500L
            }
        }
        
        return baseLatency + hopLatency + signalPenalty
    }

    /**
     * إضافة رسالة لطابور الانتظار
     */
    private fun addToPendingQueue(message: Message, targetDeviceId: String) {
        val pendingMessage = PendingRoutedMessage(
            message = message,
            targetDeviceId = targetDeviceId,
            timestamp = System.currentTimeMillis(),
            retryCount = 0
        )
        
        synchronized(pendingMessages) {
            pendingMessages.add(pendingMessage)
        }
    }

    /**
     * بدء معالج الرسائل المعلقة
     */
    private fun startMessageProcessor() {
        coroutineScope.launch {
            while (true) {
                delay(MESSAGE_RETRY_DELAY)
                processPendingMessages()
            }
        }
    }

    /**
     * معالجة الرسائل المعلقة
     */
    private suspend fun processPendingMessages() {
        synchronized(pendingMessages) {
            val iterator = pendingMessages.iterator()
            while (iterator.hasNext()) {
                val pendingMessage = iterator.next()
                
                // فحص انتهاء الصلاحية
                val age = System.currentTimeMillis() - pendingMessage.timestamp
                if (age > 30 * 60 * 1000) { // 30 دقيقة
                    iterator.remove()
                    continue
                }

                // فحص عدد المحاولات
                if (pendingMessage.retryCount >= MAX_ROUTING_ATTEMPTS) {
                    iterator.remove()
                    continue
                }

                // محاولة الإرسال مرة أخرى
                coroutineScope.launch {
                    val result = routeMessage(pendingMessage.message, pendingMessage.targetDeviceId)
                    if (result == RoutingResult.SUCCESS) {
                        synchronized(pendingMessages) {
                            pendingMessages.remove(pendingMessage)
                        }
                    } else {
                        pendingMessage.retryCount++
                    }
                }
            }
        }
    }

    /**
     * بدء اكتشاف المسارات الدوري
     */
    private fun startRouteDiscovery() {
        coroutineScope.launch {
            while (true) {
                delay(30000) // كل 30 ثانية
                updateAvailableRoutes()
            }
        }
    }

    /**
     * تحديث المسارات المتاحة
     */
    private suspend fun updateAvailableRoutes() {
        val allRoutes = mutableListOf<RoutingPath>()
        
        // إضافة المسارات المباشرة
        allRoutes.addAll(findDirectBitChatRoutes("all"))
        
        // إضافة مسارات التمرير العام
        allRoutes.addAll(findUniversalRelayRoutes("all"))
        
        _availableRoutes.value = allRoutes
    }

    /**
     * تحديث إحصائيات التوجيه
     */
    private fun updateRoutingStats(success: Boolean, route: RoutingPath) {
        val currentStats = _routingStats.value
        _routingStats.value = currentStats.copy(
            totalMessages = currentStats.totalMessages + 1,
            successfulMessages = if (success) currentStats.successfulMessages + 1 else currentStats.successfulMessages,
            averageLatency = (currentStats.averageLatency + route.latency) / 2,
            preferredRouteType = route.routeType
        )
    }

    /**
     * الحصول على معرف الجهاز الحالي
     */
    private fun getCurrentDeviceId(): String {
        return "current_device_id" // يجب الحصول عليه من مكان آخر
    }

    /**
     * تنظيف الموارد
     */
    fun destroy() {
        coroutineScope.cancel()
    }
}

/**
 * حالة نظام التوجيه
 */
enum class RoutingStatus {
    IDLE,
    ROUTING,
    DISCOVERING,
    ERROR
}

/**
 * نوع المسار
 */
enum class RouteType {
    DIRECT_BITCHAT,
    MESH_NETWORK,
    UNIVERSAL_RELAY,
    HYBRID
}

/**
 * نتيجة التوجيه
 */
enum class RoutingResult {
    SUCCESS,
    NO_ROUTE_FOUND,
    QUEUED,
    ERROR
}

/**
 * مسار التوجيه
 */
data class RoutingPath(
    val routeType: RouteType,
    val hops: List<String>,
    val reliability: Float,
    val latency: Long,
    val priority: Int
)

/**
 * رسالة معلقة
 */
data class PendingRoutedMessage(
    val message: Message,
    val targetDeviceId: String,
    val timestamp: Long,
    var retryCount: Int
)

/**
 * حزمة اكتشاف المسار
 */
@kotlinx.serialization.Serializable
data class RouteDiscoveryPacket(
    val searchId: String,
    val sourceDeviceId: String,
    val targetDeviceId: String,
    val hopCount: Int,
    val timestamp: Long
)

/**
 * إحصائيات التوجيه
 */
data class RoutingStatistics(
    val totalMessages: Long = 0,
    val successfulMessages: Long = 0,
    val averageLatency: Long = 0,
    val preferredRouteType: RouteType? = null
)