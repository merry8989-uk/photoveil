package io.github.merry8989uk.photoveil.settings

import io.github.merry8989uk.photoveil.core.OutputFormat
import io.github.merry8989uk.photoveil.core.ShieldMode

/** Everything the Settings screen can control. */
data class SettingsState(
    // Appearance
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val accentColor: Long = 0xFF7C5CFF,
    val textScale: Float = 1.0f,
    val density: Float = 1.0f,
    // General
    val notificationsEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    // Shield defaults
    val shieldMode: ShieldMode = ShieldMode.STANDARD,
    val shieldStrength: Float = 0.6f,
    val freshSeedPerImage: Boolean = true,
    val watermarkText: String = "AI-PROTECTED",
    // Output
    val outputFormat: OutputFormat = OutputFormat.PNG,
    val jpegQuality: Int = 92,
    val maxDimension: Int = 4096,
    val saveToGallery: Boolean = true,
    val autoShareAfterSave: Boolean = false,
    // Diagnostics — on by default
    val diagnosticsEnabled: Boolean = true,
)

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Accent choices offered in Settings. */
val ACCENT_CHOICES: List<Pair<String, Long>> = listOf(
    "Violet" to 0xFF7C5CFF,
    "Teal" to 0xFF3BE0C0,
    "Blue" to 0xFF4C8DFF,
    "Amber" to 0xFFFFB443,
    "Rose" to 0xFFFF6B9D,
)
