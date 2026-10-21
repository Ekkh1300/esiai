package com.sokhan.chat.ui.navigation

import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController

/** Route table. Kept as constants + typed builders to avoid string drift. */
object Routes {
    const val CHATS = "chats"
    const val ASSISTANTS = "assistants"
    const val SETTINGS = "settings"
    const val CHAT = "chat/{conversationId}"
    const val PROVIDERS = "providers"
    const val PROVIDER_EDITOR = "providerEditor/{providerId}"
    const val ASSISTANT_EDITOR = "assistantEditor/{assistantId}"
    const val ABOUT = "about"
    const val LICENSES = "licenses"

    fun chat(conversationId: String) = "chat/$conversationId"
    fun providerEditor(id: String?) = "providerEditor/${id ?: "new"}"
    fun assistantEditor(id: String?) = "assistantEditor/${id ?: "new"}"
}

fun NavHostController.bringToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
