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
import com.bitchat.app.presentation.screens.main.CallHistory
import com.bitchat.app.presentation.screens.main.CallType
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun CallsTab(
    callHistory: List<CallHistory>,
    onCallClick: (CallHistory) -> Unit,
    modifier: Modifier = Modifier
) {
    if (callHistory.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Call,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "لا توجد مكالمات",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "ستظهر مكالماتك الصوتية والمرئية هنا",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = modifier,
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(callHistory) { call ->
                CallItem(
                    call = call,
                    onClick = { onCallClick(call) }
                )
            }
        }
    }
}

@Composable
private fun CallItem(
    call: CallHistory,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
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
            // Call Icon with status
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            !call.isAnswered && call.isIncoming -> MaterialTheme.colorScheme.errorContainer
                            call.callType == CallType.VIDEO -> MaterialTheme.colorScheme.secondaryContainer
                            else -> MaterialTheme.colorScheme.primaryContainer
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        !call.isAnswered && call.isIncoming -> Icons.Default.CallReceived
                        !call.isAnswered && !call.isIncoming -> Icons.Default.CallMade
                        call.isIncoming -> Icons.Default.CallReceived
                        else -> Icons.Default.CallMade
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = when {
                        !call.isAnswered && call.isIncoming -> MaterialTheme.colorScheme.onErrorContainer
                        call.callType == CallType.VIDEO -> MaterialTheme.colorScheme.onSecondaryContainer
                        else -> MaterialTheme.colorScheme.onPrimaryContainer
                    }
                )
                
                // Call type indicator
                if (call.callType == CallType.VIDEO) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = "مكالمة فيديو",
                        modifier = Modifier
                            .size(16.dp)
                            .offset(x = 12.dp, y = (-12).dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Call Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = call.contactName,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    Text(
                        text = formatCallTime(call.timestamp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Call status and duration
                    Text(
                        text = getCallStatusText(call),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            !call.isAnswered && call.isIncoming -> MaterialTheme.colorScheme.error
                            !call.isAnswered && !call.isIncoming -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f)
                    )

                    // Action buttons
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Call back button
                        IconButton(
                            onClick = { /* Handle call back */ },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (call.callType == CallType.VIDEO) 
                                    Icons.Default.Videocam 
                                else Icons.Default.Call,
                                contentDescription = "إعادة الاتصال",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Info button
                        IconButton(
                            onClick = { /* Handle info */ },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "تفاصيل المكالمة",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun getCallStatusText(call: CallHistory): String {
    return when {
        !call.isAnswered && call.isIncoming -> "مكالمة فائتة"
        !call.isAnswered && !call.isIncoming -> "لم يتم الرد"
        call.duration > 0 -> "${formatDuration(call.duration)} • ${if (call.isIncoming) "واردة" else "صادرة"}"
        else -> if (call.isIncoming) "مكالمة واردة" else "مكالمة صادرة"
    }
}

private fun formatDuration(seconds: Int): String {
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val secs = seconds % 60
    
    return when {
        hours > 0 -> String.format("%d:%02d:%02d", hours, minutes, secs)
        else -> String.format("%d:%02d", minutes, secs)
    }
}

private fun formatCallTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 24 * 60 * 60 * 1000 -> {
            // Today - show hour:minute
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        diff < 48 * 60 * 60 * 1000 -> "أمس" // Yesterday
        diff < 7 * 24 * 60 * 60 * 1000 -> {
            // This week - show day name
            SimpleDateFormat("EEEE", Locale("ar")).format(Date(timestamp))
        }
        else -> {
            // Older - show date
            SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
        }
    }
}