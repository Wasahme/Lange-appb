package com.bitchat.app.presentation.screens.main.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bitchat.app.data.entities.Device
import com.bitchat.app.data.entities.DeviceType
import kotlin.math.roundToInt

@Composable
fun DevicesTab(
    devices: List<Device>,
    onDeviceClick: (Device) -> Unit,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Network Stats Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "إحصائيات الشبكة",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    NetworkStatItem(
                        label = "الأجهزة المتصلة",
                        value = devices.count { it.isOnline }.toString(),
                        icon = Icons.Default.DevicesOther,
                        color = MaterialTheme.colorScheme.primary
                    )
                    
                    NetworkStatItem(
                        label = "مع BitChat",
                        value = devices.count { it.capabilities.hasBitChat }.toString(),
                        icon = Icons.Default.Chat,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    NetworkStatItem(
                        label = "المدى الأقصى",
                        value = "${devices.maxOfOrNull { it.hopCount } ?: 0} هوب",
                        icon = Icons.Default.Timeline,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        if (devices.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.DeviceHub,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "لم يتم العثور على أجهزة",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "تأكد من تفعيل البلوتوث وقربك من أجهزة أخرى",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("بحث مرة أخرى")
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(devices) { device ->
                    DeviceItem(
                        device = device,
                        onClick = { onDeviceClick(device) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkStatItem(
    label: String,
    value: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DeviceItem(
    device: Device,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Device Icon
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            device.capabilities.hasBitChat -> MaterialTheme.colorScheme.primaryContainer
                            device.isOnline -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = getDeviceIcon(device.deviceType),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = when {
                        device.capabilities.hasBitChat -> MaterialTheme.colorScheme.onPrimaryContainer
                        device.isOnline -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Device Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = device.deviceName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    // Status indicator
                    StatusIndicator(
                        isOnline = device.isOnline,
                        hasBitChat = device.capabilities.hasBitChat
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Connection details
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        if (device.distance != null && device.distance > 0) {
                            Text(
                                text = "${device.distance.roundToInt()}م • ${getSignalStrengthText(device.signalStrength)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        if (device.hopCount > 1) {
                            Text(
                                text = "${device.hopCount} هوب • ${if (device.isDirectlyConnected) "مباشر" else "غير مباشر"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Capabilities
                if (device.capabilities.hasBitChat) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (device.capabilities.supportsMessaging) {
                            CapabilityChip(
                                icon = Icons.Default.Message,
                                label = "رسائل"
                            )
                        }
                        if (device.capabilities.supportsVoiceCalls) {
                            CapabilityChip(
                                icon = Icons.Default.Call,
                                label = "صوت"
                            )
                        }
                        if (device.capabilities.supportsVideoCalls) {
                            CapabilityChip(
                                icon = Icons.Default.Videocam,
                                label = "فيديو"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusIndicator(
    isOnline: Boolean,
    hasBitChat: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (hasBitChat) {
            Icon(
                imageVector = Icons.Default.Verified,
                contentDescription = "يدعم BitChat",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
        
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (isOnline) 
                        MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
        )
    }
}

@Composable
private fun CapabilityChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.height(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

private fun getDeviceIcon(deviceType: DeviceType): androidx.compose.ui.graphics.vector.ImageVector {
    return when (deviceType) {
        DeviceType.ANDROID_PHONE -> Icons.Default.PhoneAndroid
        DeviceType.ANDROID_TABLET -> Icons.Default.Tablet
        DeviceType.IPHONE -> Icons.Default.PhoneIphone
        DeviceType.IPAD -> Icons.Default.TabletMac
        DeviceType.WINDOWS_PC -> Icons.Default.Computer
        DeviceType.MAC -> Icons.Default.Laptop
        DeviceType.LINUX_PC -> Icons.Default.DesktopWindows
        DeviceType.RASPBERRY_PI -> Icons.Default.Memory
        DeviceType.UNKNOWN -> Icons.Default.DeviceUnknown
    }
}

private fun getSignalStrengthText(rssi: Int): String {
    return when {
        rssi >= -50 -> "قوي جداً"
        rssi >= -60 -> "قوي"
        rssi >= -70 -> "متوسط"
        rssi >= -80 -> "ضعيف"
        else -> "ضعيف جداً"
    }
}