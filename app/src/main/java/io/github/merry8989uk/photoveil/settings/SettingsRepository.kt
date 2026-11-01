package io.github.merry8989uk.photoveil.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import io.github.merry8989uk.photoveil.core.OutputFormat
import io.github.merry8989uk.photoveil.core.ShieldMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "photoveil_settings")

/**
 * Persists every setting to DataStore. Reads are a Flow, so a change is visible
 * immediately everywhere it matters — no dead toggles.
 */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACCENT = longPreferencesKey("accent_color")
        val TEXT_SCALE = floatPreferencesKey("text_scale")
        val DENSITY = floatPreferencesKey("density")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val HAPTICS = booleanPreferencesKey("haptics")
        val SHIELD_MODE = stringPreferencesKey("shield_mode")
        val SHIELD_STRENGTH = floatPreferencesKey("shield_strength")
        val FRESH_SEED = booleanPreferencesKey("fresh_seed")
        val WATERMARK_TEXT = stringPreferencesKey("watermark_text")
        val FORMAT = stringPreferencesKey("output_format")
        val JPEG_QUALITY = intPreferencesKey("jpeg_quality")
        val MAX_DIMENSION = intPreferencesKey("max_dimension")
        val SAVE_TO_GALLERY = booleanPreferencesKey("save_to_gallery")
        val AUTO_SHARE = booleanPreferencesKey("auto_share")
        val DIAGNOSTICS = booleanPreferencesKey("diagnostics_enabled")
    }

    val settings: Flow<SettingsState> = context.dataStore.data.map { p ->
        SettingsState(
            themeMode = p[Keys.THEME_MODE]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            accentColor = p[Keys.ACCENT] ?: 0xFF7C5CFF,
            textScale = p[Keys.TEXT_SCALE] ?: 1.0f,
            density = p[Keys.DENSITY] ?: 1.0f,
            notificationsEnabled = p[Keys.NOTIFICATIONS] ?: true,
            hapticsEnabled = p[Keys.HAPTICS] ?: true,
            shieldMode = p[Keys.SHIELD_MODE]
                ?.let { runCatching { ShieldMode.valueOf(it) }.getOrNull() }
                ?: ShieldMode.STANDARD,
            shieldStrength = p[Keys.SHIELD_STRENGTH] ?: 0.6f,
            freshSeedPerImage = p[Keys.FRESH_SEED] ?: true,
            watermarkText = p[Keys.WATERMARK_TEXT] ?: "AI-PROTECTED",
            outputFormat = p[Keys.FORMAT]
                ?.let { runCatching { OutputFormat.valueOf(it) }.getOrNull() }
                ?: OutputFormat.PNG,
            jpegQuality = p[Keys.JPEG_QUALITY] ?: 92,
            maxDimension = p[Keys.MAX_DIMENSION] ?: 4096,
            saveToGallery = p[Keys.SAVE_TO_GALLERY] ?: true,
            autoShareAfterSave = p[Keys.AUTO_SHARE] ?: false,
            diagnosticsEnabled = p[Keys.DIAGNOSTICS] ?: true,
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) =
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }

    suspend fun setAccentColor(color: Long) =
        context.dataStore.edit { it[Keys.ACCENT] = color }

    suspend fun setTextScale(scale: Float) =
        context.dataStore.edit { it[Keys.TEXT_SCALE] = scale }

    suspend fun setDensity(value: Float) =
        context.dataStore.edit { it[Keys.DENSITY] = value }

    suspend fun setNotifications(enabled: Boolean) =
        context.dataStore.edit { it[Keys.NOTIFICATIONS] = enabled }

    suspend fun setHaptics(enabled: Boolean) =
        context.dataStore.edit { it[Keys.HAPTICS] = enabled }

    suspend fun setShieldMode(mode: ShieldMode) =
        context.dataStore.edit { it[Keys.SHIELD_MODE] = mode.name }

    suspend fun setShieldStrength(value: Float) =
        context.dataStore.edit { it[Keys.SHIELD_STRENGTH] = value }

    suspend fun setFreshSeed(enabled: Boolean) =
        context.dataStore.edit { it[Keys.FRESH_SEED] = enabled }

    suspend fun setWatermarkText(text: String) =
        context.dataStore.edit { it[Keys.WATERMARK_TEXT] = text }

    suspend fun setOutputFormat(format: OutputFormat) =
        context.dataStore.edit { it[Keys.FORMAT] = format.name }

    suspend fun setJpegQuality(quality: Int) =
        context.dataStore.edit { it[Keys.JPEG_QUALITY] = quality }

    suspend fun setMaxDimension(value: Int) =
        context.dataStore.edit { it[Keys.MAX_DIMENSION] = value }

    suspend fun setSaveToGallery(enabled: Boolean) =
        context.dataStore.edit { it[Keys.SAVE_TO_GALLERY] = enabled }

    suspend fun setAutoShare(enabled: Boolean) =
        context.dataStore.edit { it[Keys.AUTO_SHARE] = enabled }

    suspend fun setDiagnosticsEnabled(enabled: Boolean) =
        context.dataStore.edit { it[Keys.DIAGNOSTICS] = enabled }

    suspend fun reset() = context.dataStore.edit { it.clear() }
}
