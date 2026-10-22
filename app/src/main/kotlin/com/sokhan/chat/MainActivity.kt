package com.sokhan.chat

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sokhan.chat.domain.model.ThemeMode
import com.sokhan.chat.domain.repository.SettingsRepository
import com.sokhan.chat.ui.LocalizedApp
import com.sokhan.chat.ui.SokhanRoot
import com.sokhan.chat.ui.theme.SokhanTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    /**
     * Locale-aware resources for every window this activity owns.
     *
     * Compose dialogs and popups are rendered in their own windows whose
     * context derives from this activity — so wrapping only LocalContext
     * (see [LocalizedApp]) would leave them in the *system* language while
     * the rest of the UI follows the in-app language. Overriding
     * [getResources] fixes all of them at once, and it updates live when the
     * user switches language (no activity restart needed).
     */
    private var localeResources: Resources? = null

    override fun getResources(): Resources = localeResources ?: super.getResources()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Koin is started in SokhanApplication.beforeOnCreate; reading settings
        // here avoids a ViewModel round-trip before the first composition.
        val koin = runCatching { org.koin.core.context.GlobalContext.get() }.getOrNull()
            ?: error("Koin has not been started — missing SokhanApplication")
        val settings = koin.get<SettingsRepository>()

        setContent {
            // The locale-wrapping below replaces LocalContext with a plain
            // ContextThemeWrapper, so rememberLauncherForActivityResult's
            // "fall back to the context" path can no longer find the Activity.
            // Provide the owners explicitly at the root of the composition —
            // they are activity-scoped by nature and must never disappear.
            CompositionLocalProvider(
                LocalActivityResultRegistryOwner provides this,
                LocalOnBackPressedDispatcherOwner provides this,
            ) {
                val snapshot by settings.settings.collectAsStateWithLifecycle()
                // Keep the activity resources in sync with the chosen language
                // so dialogs/popups (separate windows) speak the same language.
                LaunchedEffect(snapshot.language) { applyAppLocale(snapshot.language) }
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

    /** Rebuilds the locale-specific [Resources] used by every window. */
    private fun applyAppLocale(languageKey: String) {
        if (languageKey == "system") {
            localeResources = null
            return
        }
        val base = super.getResources()
        val locale = Locale.forLanguageTag(languageKey)
        val config = Configuration(base.configuration).apply {
            setLocale(locale)
            setLayoutDirection(locale)
        }
        localeResources = createConfigurationContext(config).resources
    }
}
