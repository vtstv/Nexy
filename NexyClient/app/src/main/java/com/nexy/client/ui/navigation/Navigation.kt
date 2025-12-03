package com.nexy.client.ui.navigation

sealed class Screen(val route: String) {
    object Auth : Screen("auth")
    object Login : Screen("login")
    object Register : Screen("register")
    object ChatList : Screen("chat_list")
    object Chat : Screen("chat/{chatId}") {
        fun createRoute(chatId: Int) = "chat/$chatId"
    }
    object Profile : Screen("profile/{userId}") {
        fun createRoute(userId: Int) = "profile/$userId"
    }
    object Search : Screen("search")
    object Contacts : Screen("contacts")
    object Invite : Screen("invite/{chatId}") {
        fun createRoute(chatId: Int) = "invite/$chatId"
    }
    object QRScanner : Screen("qr_scanner")
    object Call : Screen("call/{callId}") {
        fun createRoute(callId: String) = "call/$callId"
    }
    object Settings : Screen("settings")
    object CreateGroup : Screen("create_group")
    object GroupSettings : Screen("group_settings/{groupId}") {
        fun createRoute(groupId: Int) = "group_settings/$groupId"
    }
    object EditGroup : Screen("edit_group/{groupId}") {
        fun createRoute(groupId: Int) = "edit_group/$groupId"
    }
    object SearchGroups : Screen("search_groups")
}
