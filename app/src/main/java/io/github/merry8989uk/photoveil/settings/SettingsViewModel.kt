package io.github.merry8989uk.photoveil.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.merry8989uk.photoveil.core.OutputFormat
import io.github.merry8989uk.photoveil.core.ShieldMode
import io.github.merry8989uk.photoveil.diagnostics.DiagnosticsLogger
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = SettingsRepository(app)

    val state: StateFlow<SettingsState> = repo.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsState())

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch { repo.setThemeMode(mode) }
    fun setAccentColor(color: Long) = viewModelScope.launch { repo.setAccentColor(color) }
    fun setTextScale(scale: Float) = viewModelScope.launch { repo.setTextScale(scale) }
    fun setDensity(value: Float) = viewModelScope.launch { repo.setDensity(value) }
    fun setNotifications(enabled: Boolean) = viewModelScope.launch { repo.setNotifications(enabled) }
    fun setHaptics(enabled: Boolean) = viewModelScope.launch { repo.setHaptics(enabled) }
    fun setShieldMode(mode: ShieldMode) = viewModelScope.launch { repo.setShieldMode(mode) }
    fun setShieldStrength(value: Float) = viewModelScope.launch { repo.setShieldStrength(value) }
    fun setFreshSeed(enabled: Boolean) = viewModelScope.launch { repo.setFreshSeed(enabled) }
    fun setWatermarkText(text: String) = viewModelScope.launch { repo.setWatermarkText(text) }
    fun setOutputFormat(format: OutputFormat) = viewModelScope.launch { repo.setOutputFormat(format) }
    fun setJpegQuality(quality: Int) = viewModelScope.launch { repo.setJpegQuality(quality) }
    fun setMaxDimension(value: Int) = viewModelScope.launch { repo.setMaxDimension(value) }
    fun setSaveToGallery(enabled: Boolean) = viewModelScope.launch { repo.setSaveToGallery(enabled) }
    fun setAutoShare(enabled: Boolean) = viewModelScope.launch { repo.setAutoShare(enabled) }

    fun setDiagnosticsEnabled(enabled: Boolean) = viewModelScope.launch {
        repo.setDiagnosticsEnabled(enabled)
        DiagnosticsLogger.setEnabled(enabled)
    }

    fun reset() = viewModelScope.launch { repo.reset() }
}
