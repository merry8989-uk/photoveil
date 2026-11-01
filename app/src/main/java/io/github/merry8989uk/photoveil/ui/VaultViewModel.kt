package io.github.merry8989uk.photoveil.ui

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.merry8989uk.photoveil.core.ImageIo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class VaultItem(
    val file: File,
    val thumb: Bitmap?,
)

class VaultViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext

    var items by mutableStateOf<List<VaultItem>>(emptyList())
        private set
    var totalBytes by mutableStateOf(0L)
        private set
    var busy by mutableStateOf(false)
        private set

    fun refresh() {
        busy = true
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                ImageIo.listVault(context).map { file ->
                    VaultItem(file = file, thumb = ImageIo.decodeFile(file, 320))
                }
            }
            items = loaded
            totalBytes = loaded.sumOf { it.file.length() }
            busy = false
        }
    }

    fun share(item: VaultItem) {
        val mime = if (item.file.extension.equals("png", true)) "image/png" else "image/jpeg"
        runCatching { ImageIo.shareFile(context, item.file, mime) }
    }

    fun delete(item: VaultItem) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { item.file.delete() } }
            refresh()
        }
    }
}
