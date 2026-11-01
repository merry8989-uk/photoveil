package io.github.merry8989uk.photoveil.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.merry8989uk.photoveil.core.ImageIo
import io.github.merry8989uk.photoveil.core.ShieldEngine
import io.github.merry8989uk.photoveil.core.ShieldMode
import io.github.merry8989uk.photoveil.core.ShieldParams
import io.github.merry8989uk.photoveil.core.ShieldStats
import io.github.merry8989uk.photoveil.diagnostics.DiagnosticsLogger
import io.github.merry8989uk.photoveil.settings.SettingsRepository
import io.github.merry8989uk.photoveil.settings.SettingsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class ShieldedItem(
    val name: String,
    val bitmap: Bitmap,
    val stats: ShieldStats,
    val file: File?,
)

/** Drives the Shield tab: picking, shielding, saving, sharing. */
class ShieldViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext
    private val repo = SettingsRepository(context)

    val settings: StateFlow<SettingsState> = repo.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, SettingsState())

    var picked by mutableStateOf<List<Uri>>(emptyList())
        private set
    var preview by mutableStateOf<Bitmap?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var progress by mutableStateOf(0f)
        private set
    var status by mutableStateOf<String?>(null)
        private set
    var results by mutableStateOf<List<ShieldedItem>>(emptyList())
        private set

    fun setMode(mode: ShieldMode) = viewModelScope.launch { repo.setShieldMode(mode) }
    fun setStrength(value: Float) = viewModelScope.launch { repo.setShieldStrength(value) }

    fun onPicked(uris: List<Uri>) {
        picked = uris
        results = emptyList()
        status = "${uris.size} photo(s) ready."
        val first = uris.firstOrNull()
        if (first == null) {
            preview = null
            return
        }
        viewModelScope.launch {
            preview = withContext(Dispatchers.IO) {
                ImageIo.decode(context, first, 1400)
            }
        }
    }

    fun clear() {
        picked = emptyList()
        preview = null
        results = emptyList()
        progress = 0f
        status = null
    }

    fun applyShield() {
        if (busy) return
        val uris = picked
        if (uris.isEmpty()) {
            status = "Pick a photo first."
            return
        }
        val config = settings.value
        busy = true
        progress = 0f
        results = emptyList()
        viewModelScope.launch {
            val done = mutableListOf<ShieldedItem>()
            uris.forEachIndexed { index, uri ->
                status = "Shielding ${index + 1} of ${uris.size}…"
                val item: ShieldedItem? = withContext(Dispatchers.Default) {
                    runCatching {
                        val source = ImageIo.decode(context, uri, config.maxDimension)
                            ?: return@runCatching null
                        val seed = if (config.freshSeedPerImage) {
                            (System.nanoTime() xor (index * 0x9E3779B9L)).toInt()
                        } else {
                            ShieldParams.DEFAULT_SEED
                        }
                        val params = ShieldParams(
                            mode = config.shieldMode,
                            strength = config.shieldStrength,
                            seed = seed,
                            watermarkText = config.watermarkText,
                        )
                        val result = ShieldEngine.shield(source, params)
                        val file = ImageIo.saveToVault(
                            context, result.bitmap, config.outputFormat, config.jpegQuality, index,
                        )
                        if (config.saveToGallery) {
                            ImageIo.saveToGallery(
                                context, result.bitmap, config.outputFormat,
                                config.jpegQuality, file.name,
                            )
                        }
                        DiagnosticsLogger.log(
                            "Shield",
                            "ok ${result.bitmap.width}x${result.bitmap.height} " +
                                "mode=${params.mode} mean=${"%.2f".format(result.stats.meanAbsDelta)} " +
                                "max=${result.stats.maxDelta} ms=${result.stats.elapsedMs}",
                        )
                        ShieldedItem(
                            name = file.name,
                            bitmap = result.bitmap,
                            stats = result.stats,
                            file = file,
                        )
                    }.getOrNull()
                }
                if (item != null) done.add(item)
                progress = (index + 1).toFloat() / uris.size
            }
            results = done
            busy = false
            status = if (done.isEmpty()) {
                "Those images could not be read."
            } else {
                "Shielded ${done.size} image(s). Saved to the Vault."
            }
            if (done.isNotEmpty() && config.autoShareAfterSave) {
                share(done.first())
            }
        }
    }

    fun share(item: ShieldedItem) {
        val file = item.file ?: return
        val mime = if (file.extension.equals("png", true)) "image/png" else "image/jpeg"
        runCatching { ImageIo.shareFile(context, file, mime) }
    }

    fun saveToGallery(item: ShieldedItem) {
        val config = settings.value
        viewModelScope.launch {
            val uri = withContext(Dispatchers.IO) {
                ImageIo.saveToGallery(
                    context, item.bitmap, config.outputFormat, config.jpegQuality, item.name,
                )
            }
            status = if (uri != null) "Saved to your gallery." else "Could not write to the gallery."
        }
    }
}
