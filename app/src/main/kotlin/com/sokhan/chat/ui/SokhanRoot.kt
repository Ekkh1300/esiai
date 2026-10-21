package com.sokhan.chat.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.sokhan.chat.R
import com.sokhan.chat.domain.model.SettingsSnapshot
import com.sokhan.chat.ui.navigation.Routes
import com.sokhan.chat.ui.navigation.bringToTab
import com.sokhan.chat.ui.screens.about.AboutScreen
import com.sokhan.chat.ui.screens.about.LicensesScreen
import com.sokhan.chat.ui.screens.assistants.AssistantEditorScreen
import com.sokhan.chat.ui.screens.assistants.AssistantsScreen
import com.sokhan.chat.ui.screens.chat.ChatScreen
import com.sokhan.chat.ui.screens.chats.ChatsScreen
import com.sokhan.chat.ui.screens.providers.ProviderEditorScreen
import com.sokhan.chat.ui.screens.providers.ProvidersScreen
import com.sokhan.chat.ui.screens.settings.SettingsScreen

private data class Tab(val route: String, val labelRes: Int)

@Composable
fun SokhanRoot(settings: SettingsSnapshot) {
    val navController = rememberNavController()
    val tabs = remember {
        listOf(
            Tab(Routes.CHATS, R.string.nav_chats),
            Tab(Routes.ASSISTANTS, R.string.nav_assistants),
            Tab(Routes.SETTINGS, R.string.nav_settings)
        )
    }

    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in tabs.map { it.route }) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = { navController.bringToTab(tab.route) },
                            icon = {
                                when (tab.route) {
                                    Routes.CHATS -> Icon(
                                        Icons.Filled.Forum,
                                        contentDescription = null
                                    )
                                    Routes.ASSISTANTS -> Icon(
                                        Icons.Filled.SmartToy,
                                        contentDescription = null
                                    )
                                    else -> Icon(
                                        Icons.Filled.Settings,
                                        contentDescription = null
                                    )
                                }
                            },
                            label = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            settings = settings,
            modifier = Modifier.padding(bottom = innerPadding.calculateBottomPadding())
        )
    }
}

@Composable
private fun AppNavHost(
    navController: NavHostController,
    settings: SettingsSnapshot,
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = Routes.CHATS,
        modifier = modifier
    ) {
        composable(Routes.CHATS) {
            ChatsScreen(
                onOpenChat = { id -> navController.navigate(Routes.chat(id)) },
                onNewChat = { id -> navController.navigate(Routes.chat(id)) },
                onOpenProviders = { navController.navigate(Routes.PROVIDERS) }
            )
        }
        composable(Routes.ASSISTANTS) {
            AssistantsScreen(
                onStartChat = { id -> navController.navigate(Routes.chat(id)) },
                onEditAssistant = { id -> navController.navigate(Routes.assistantEditor(id)) },
                onNewAssistant = { navController.navigate(Routes.assistantEditor(null)) }
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onOpenProviders = { navController.navigate(Routes.PROVIDERS) },
                onOpenAbout = { navController.navigate(Routes.ABOUT) },
                onOpenLicenses = { navController.navigate(Routes.LICENSES) }
            )
        }
        composable(Routes.PROVIDERS) {
            ProvidersScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(Routes.providerEditor(id)) },
                onNew = { navController.navigate(Routes.providerEditor(null)) }
            )
        }
        composable(Routes.PROVIDER_EDITOR) { entry ->
            val id = entry.arguments?.getString("providerId")?.takeIf { it != "new" }
            ProviderEditorScreen(
                providerId = id,
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.ASSISTANT_EDITOR) { entry ->
            val id = entry.arguments?.getString("assistantId")?.takeIf { it != "new" }
            AssistantEditorScreen(
                assistantId = id,
                onBack = { navController.popBackStack() },
                onStartChat = { conversationId ->
                    navController.navigate(Routes.chat(conversationId)) {
                        popUpTo(Routes.ASSISTANTS)
                    }
                }
            )
        }
        composable(Routes.CHAT) { entry ->
            val id = entry.arguments?.getString("conversationId").orEmpty()
            ChatScreen(
                conversationId = id,
                onBack = { navController.popBackStack() },
                onOpenProviderEditor = { providerId ->
                    navController.navigate(Routes.providerEditor(providerId))
                }
            )
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.LICENSES) {
            LicensesScreen(onBack = { navController.popBackStack() })
        }
    }
}
