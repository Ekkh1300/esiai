package com.sokhan.chat.ui

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import java.util.Locale

/**
 * In-app language switching without an activity restart.
 * Wraps the current context with a locale-specific configuration context and
 * feeds it into the composition (LocalContext/LocalConfiguration), together
 * with a matching layout direction for full RTL support.
 */
object AppLocale {
    val SUPPORTED = listOf("system", "en", "fa")

    fun wrap(base: Context, languageKey: String): Context {
        if (languageKey == "system") return base
        val locale = Locale.forLanguageTag(languageKey)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }
}

@Composable
fun LocalizedApp(
    languageKey: String,
    content: @Composable () -> Unit
) {
    val baseContext = LocalContext.current
    val wrapped = remember(languageKey, baseContext) {
        AppLocale.wrap(baseContext, languageKey)
    }
    val locale = remember(languageKey) {
        if (languageKey == "system") {
            wrapped.resources.configuration.locales[0]
        } else {
            Locale.forLanguageTag(languageKey)
        }
    }
    val direction = if (locale.language == "fa" || locale.language == "ar" || locale.language == "he") {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    CompositionLocalProvider(
        LocalContext provides wrapped,
        LocalConfiguration provides wrapped.resources.configuration,
        LocalLayoutDirection provides direction,
        content = content
    )
}
