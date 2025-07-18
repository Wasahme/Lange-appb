package com.bitchat.app.presentation.navigation

import androidx.navigation.NamedNavArgument
import androidx.navigation.NavType
import androidx.navigation.navArgument

sealed class Screen(
    val route: String,
    val arguments: List<NamedNavArgument> = emptyList()
) {
    object Main : Screen("main")
    
    object Chat : Screen(
        route = "chat/{chatId}",
        arguments = listOf(
            navArgument("chatId") {
                type = NavType.StringType
            }
        )
    ) {
        fun createRoute(chatId: String) = "chat/$chatId"
    }
    
    object Profile : Screen("profile")
    
    object Devices : Screen("devices")
    
    object Settings : Screen("settings")
    
    object CreateProfile : Screen("create_profile")
    
    object MeshNetwork : Screen("mesh_network")
    
    object Calls : Screen("calls")
    
    object FileTransfer : Screen("file_transfer")
}