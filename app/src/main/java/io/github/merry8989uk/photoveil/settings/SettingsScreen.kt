package io.github.merry8989uk.photoveil.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.merry8989uk.photoveil.core.OutputFormat
import io.github.merry8989uk.photoveil.core.ShieldEngine
import io.github.merry8989uk.photoveil.core.ShieldMode
import io.github.merry8989uk.photoveil.diagnostics.DiagnosticsSection

private val MAX_DIMENSIONS = listOf(2048, 3072, 4096, 6000)

/**
 * Settings, in the fixed order: Appearance → Shield defaults → General →
 * Diagnostics → Reset → About.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ---------- 1. Appearance ----------
            SectionTitle("Appearance")

            Text("Theme", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.themeMode == mode,
                        onClick = { vm.setThemeMode(mode) },
                        label = {
                            Text(mode.name.lowercase().replaceFirstChar { it.uppercase() })
                        },
                    )
                }
            }

            Text("Accent colour", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ACCENT_CHOICES.forEach { (name, argb) ->
                    FilterChip(
                        selected = state.accentColor == argb,
                        onClick = { vm.setAccentColor(argb) },
                        label = { Text(name) },
                    )
                }
            }

            Text(
                "Text size — ${"%.2f".format(state.textScale)}×",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = state.textScale,
                onValueChange = vm::setTextScale,
                valueRange = 0.85f..1.4f,
            )

            Text(
                "Density — ${"%.2f".format(state.density)}×",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = state.density,
                onValueChange = vm::setDensity,
                valueRange = 0.85f..1.15f,
            )

            HorizontalDivider()

            // ---------- 2. Shield defaults ----------
            SectionTitle("Shield defaults")

            Text("Mode", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ShieldMode.entries.forEach { mode ->
                    FilterChip(
                        selected = state.shieldMode == mode,
                        onClick = { vm.setShieldMode(mode) },
                        label = { Text(mode.label) },
                    )
                }
            }
            Text(
                state.shieldMode.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "Strength — up to %.1f/255".format(
                    ShieldEngine.amplitudeFor(state.shieldMode, state.shieldStrength),
                ),
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = state.shieldStrength,
                onValueChange = vm::setShieldStrength,
                valueRange = 0.1f..1f,
            )

            ToggleRow(
                title = "Fresh seed per image",
                subtitle = "Each photo gets its own perturbation pattern. Turn off " +
                    "to reuse one pattern for a whole set.",
                checked = state.freshSeedPerImage,
                onChange = vm::setFreshSeed,
            )

            var watermarkDraft by remember(state.watermarkText) {
                mutableStateOf(state.watermarkText)
            }
            OutlinedTextField(
                value = watermarkDraft,
                onValueChange = {
                    watermarkDraft = it.take(24)
                    vm.setWatermarkText(watermarkDraft.ifBlank { "AI-PROTECTED" })
                },
                label = { Text("Visible-mode stamp text") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Output format", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutputFormat.entries.forEach { format ->
                    FilterChip(
                        selected = state.outputFormat == format,
                        onClick = { vm.setOutputFormat(format) },
                        label = { Text(format.label) },
                    )
                }
            }
            Text(
                if (state.outputFormat == OutputFormat.PNG) {
                    "PNG keeps the perturbation exactly as computed. Recommended."
                } else {
                    "JPEG re-compresses the pixels, so part of the shield is lost. " +
                        "Higher quality keeps more of it."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                "JPEG quality — ${state.jpegQuality}",
                style = MaterialTheme.typography.labelLarge,
            )
            Slider(
                value = state.jpegQuality.toFloat(),
                onValueChange = { vm.setJpegQuality(it.toInt()) },
                valueRange = 60f..100f,
                enabled = state.outputFormat == OutputFormat.JPEG,
            )

            Text("Largest side", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MAX_DIMENSIONS.forEach { size ->
                    FilterChip(
                        selected = state.maxDimension == size,
                        onClick = { vm.setMaxDimension(size) },
                        label = { Text("$size px") },
                    )
                }
            }
            Text(
                "Bigger keeps more detail and uses more memory. 4096 px is a good " +
                    "balance for modern phones.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            // ---------- 3. General ----------
            SectionTitle("General")

            ToggleRow(
                title = "Save to device gallery",
                subtitle = "Also place each shielded image in Pictures/PhotoVeil " +
                    "so other apps can see it.",
                checked = state.saveToGallery,
                onChange = vm::setSaveToGallery,
            )
            ToggleRow(
                title = "Open share sheet after shielding",
                subtitle = "Jump straight to sharing once a batch finishes.",
                checked = state.autoShareAfterSave,
                onChange = vm::setAutoShare,
            )
            ToggleRow(
                title = "Notifications",
                subtitle = "Allow PhotoVeil to notify you about finished batches " +
                    "and new versions.",
                checked = state.notificationsEnabled,
                onChange = vm::setNotifications,
            )
            ToggleRow(
                title = "Haptics",
                subtitle = "A short buzz when a shield finishes.",
                checked = state.hapticsEnabled,
                onChange = vm::setHaptics,
            )

            HorizontalDivider()

            // ---------- 4. Diagnostics ----------
            DiagnosticsSection(
                enabled = state.diagnosticsEnabled,
                onEnabledChange = vm::setDiagnosticsEnabled,
            )

            HorizontalDivider()

            // ---------- 5. Reset ----------
            SectionTitle("Reset")
            OutlinedButton(onClick = vm::reset, modifier = Modifier.fillMaxWidth()) {
                Text("Reset all settings to defaults")
            }

            HorizontalDivider()

            // ---------- 6. About ----------
            AboutSection()
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(title, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
