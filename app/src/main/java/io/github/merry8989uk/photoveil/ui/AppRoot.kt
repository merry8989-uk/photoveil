package io.github.merry8989uk.photoveil.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.merry8989uk.photoveil.settings.SettingsScreen
import io.github.merry8989uk.photoveil.settings.SettingsViewModel
import io.github.merry8989uk.photoveil.settings.ThemeMode
import io.github.merry8989uk.photoveil.ui.theme.PhotoVeilTheme

private val TABS = listOf("Shield", "Vault", "Inspect")

/** Root of the app: theme + density from Settings, then the three tabs. */
@Composable
fun AppRoot() {
    val settingsVm: SettingsViewModel = viewModel()
    val settings by settingsVm.state.collectAsState()

    var tab by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }

    val dark = when (settings.themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val baseDensity = LocalDensity.current

    PhotoVeilTheme(darkTheme = dark, accent = Color(settings.accentColor)) {
        CompositionLocalProvider(
            LocalDensity provides Density(
                density = baseDensity.density * settings.density,
                fontScale = baseDensity.fontScale * settings.textScale,
            ),
        ) {
            if (showSettings) {
                SettingsScreen(onBack = { showSettings = false })
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("PhotoVeil", style = MaterialTheme.typography.titleLarge)
                            Text(
                                "Invisible armour for your photos",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(onClick = { showSettings = true }) { Text("Settings") }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TABS.forEachIndexed { index, label ->
                            FilterChip(
                                selected = tab == index,
                                onClick = { tab = index },
                                label = { Text(label) },
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when (tab) {
                            0 -> ShieldScreen()
                            1 -> VaultScreen()
                            else -> InspectScreen()
                        }
                    }
                }
            }
        }
    }
}
