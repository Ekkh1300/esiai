package com.sokhan.chat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sokhan.chat.domain.model.ThemeMode
import com.sokhan.chat.domain.repository.SettingsRepository
import com.sokhan.chat.ui.LocalizedApp
import com.sokhan.chat.ui.SokhanRoot
import com.sokhan.chat.ui.theme.SokhanTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Koin is started in SokhanApplication.beforeOnCreate; reading settings
        // here avoids a ViewModel round-trip before the first composition.
        val koin = runCatching { org.koin.core.context.GlobalContext.get() }.getOrNull()
            ?: error("Koin has not been started — missing SokhanApplication")
        val settings = koin.get<SettingsRepository>()

        setContent {
            val snapshot by settings.settings.collectAsStateWithLifecycle()
            LocalizedApp(languageKey = snapshot.language) {
                SokhanTheme(
                    themeMode = ThemeMode.fromKey(snapshot.themeMode),
                    dynamicColors = snapshot.dynamicColors
                ) {
                    SokhanRoot(settings = snapshot)
                }
            }
        }
    }
}
