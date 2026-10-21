package com.sokhan.chat.ui.screens.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sokhan.chat.R
import com.sokhan.chat.core.BuildFlags
import com.sokhan.chat.ui.components.SectionTitle

/** Static dependency inventory — kept in one place, reviewable on every bump. */
data class LicenseEntry(val name: String, val url: String, val licenseKey: Int)

/** Static dependency inventory — kept in one place, reviewable on every bump. */
private val LICENSES = listOf(
    LicenseEntry("Kotlin & Coroutines", "https://github.com/JetBrains/kotlin", R.string.license_type_apache),
    LicenseEntry("Jetpack Compose", "https://developer.android.com/jetpack/compose", R.string.license_type_apache),
    LicenseEntry("Room", "https://developer.android.com/training/data-storage/room", R.string.license_type_apache),
    LicenseEntry("OkHttp", "https://github.com/square/okhttp", R.string.license_type_apache),
    LicenseEntry("kotlinx.serialization", "https://github.com/Kotlin/kotlinx.serialization", R.string.license_type_apache),
    LicenseEntry("DataStore", "https://developer.android.com/topic/libraries/architecture/datastore", R.string.license_type_apache),
    LicenseEntry("Coil", "https://github.com/coil-kt/coil", R.string.license_type_apache),
    LicenseEntry("Koin", "https://github.com/InsertKoinIO/koin", R.string.license_type_apache),
    LicenseEntry("commonmark-java", "https://github.com/commonmark/commonmark-java", R.string.license_type_apache)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                title = { Text(stringResource(R.string.settings_licenses_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.license_note),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            LICENSES
                .filter { entry -> entry.licenseKey != 0 }
                .forEachIndexed { index, entry ->
                    Column(Modifier.fillMaxWidth()) {
                        Text(
                            text = entry.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${entry.url} · ${stringResource(entry.licenseKey)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (index < LICENSES.size - 1) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 6.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.common_back)
                        )
                    }
                },
                title = { Text(stringResource(R.string.app_name)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.titleLarge
            )
            Text(
                text = stringResource(R.string.settings_about_body),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider()
            SectionTitle(stringResource(R.string.settings_version, BuildFlags.VERSION_NAME))
            Text(
                text = "MIT © 2026 Sokhan contributors",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
