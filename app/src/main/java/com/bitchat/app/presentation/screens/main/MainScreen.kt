package com.bitchat.app.presentation.screens.main

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bitchat.app.presentation.screens.main.tabs.ChatsTab
import com.bitchat.app.presentation.screens.main.tabs.DevicesTab
import com.bitchat.app.presentation.screens.main.tabs.CallsTab

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onNavigateToChat: (String) -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToDevices: () -> Unit,
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTabIndex by remember { mutableIntStateOf(0) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = when (selectedTabIndex) {
                            0 -> "المحادثات"
                            1 -> "الأجهزة"
                            2 -> "المكالمات"
                            else -> "BitChat"
                        },
                        textAlign = TextAlign.Center
                    )
                },
                actions = {
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "الملف الشخصي"
                        )
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "الإعدادات"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uiState.unreadChatsCount > 0) {
                                    Badge {
                                        Text(
                                            text = if (uiState.unreadChatsCount > 99) "99+" 
                                                  else uiState.unreadChatsCount.toString()
                                        )
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.Chat, contentDescription = "المحادثات")
                        }
                    },
                    label = { Text("المحادثات") },
                    selected = selectedTabIndex == 0,
                    onClick = { selectedTabIndex = 0 }
                )
                NavigationBarItem(
                    icon = {
                        BadgedBox(
                            badge = {
                                if (uiState.connectedDevicesCount > 0) {
                                    Badge {
                                        Text(uiState.connectedDevicesCount.toString())
                                    }
                                }
                            }
                        ) {
                            Icon(Icons.Default.DeviceHub, contentDescription = "الأجهزة")
                        }
                    },
                    label = { Text("الأجهزة") },
                    selected = selectedTabIndex == 1,
                    onClick = { selectedTabIndex = 1 }
                )
                NavigationBarItem(
                    icon = {
                        Icon(Icons.Default.Call, contentDescription = "المكالمات")
                    },
                    label = { Text("المكالمات") },
                    selected = selectedTabIndex == 2,
                    onClick = { selectedTabIndex = 2 }
                )
            }
        },
        floatingActionButton = {
            when (selectedTabIndex) {
                0 -> {
                    FloatingActionButton(
                        onClick = { viewModel.startNewChat() },
                        containerColor = MaterialTheme.colorScheme.primary
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "محادثة جديدة",
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
                1 -> {
                    FloatingActionButton(
                        onClick = { viewModel.refreshDevices() },
                        containerColor = MaterialTheme.colorScheme.secondary
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "تحديث الأجهزة",
                            tint = MaterialTheme.colorScheme.onSecondary
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Connection Status Card
            if (!uiState.isBluetoothEnabled || !uiState.hasLocationPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "تحذير - الشبكة الشبكية معطلة",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when {
                                !uiState.isBluetoothEnabled -> "يرجى تفعيل البلوتوث للاتصال بالأجهزة القريبة"
                                !uiState.hasLocationPermission -> "يرجى منح إذن الموقع لاكتشاف الأجهزة"
                                else -> "فحص حالة الاتصال..."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.requestPermissions() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text(
                                text = "حل المشكلة",
                                color = MaterialTheme.colorScheme.onError
                            )
                        }
                    }
                }
            }

            // Mesh Network Status
            if (uiState.isBluetoothEnabled && uiState.hasLocationPermission) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "الشبكة الشبكية نشطة",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${uiState.connectedDevicesCount} جهاز متصل",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.NetworkCheck,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Tab Content
            when (selectedTabIndex) {
                0 -> ChatsTab(
                    chats = uiState.recentChats,
                    onChatClick = onNavigateToChat,
                    modifier = Modifier.fillMaxSize()
                )
                1 -> DevicesTab(
                    devices = uiState.nearbyDevices,
                    onDeviceClick = { device -> 
                        device.userId?.let { onNavigateToChat(it) }
                    },
                    onRefresh = { viewModel.refreshDevices() },
                    modifier = Modifier.fillMaxSize()
                )
                2 -> CallsTab(
                    callHistory = uiState.recentCalls,
                    onCallClick = { call -> 
                        // Handle call click
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}