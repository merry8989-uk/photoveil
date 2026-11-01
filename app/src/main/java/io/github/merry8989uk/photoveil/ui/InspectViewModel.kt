package io.github.merry8989uk.photoveil.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.merry8989uk.photoveil.core.CompareResult
import io.github.merry8989uk.photoveil.core.FileReport
import io.github.merry8989uk.photoveil.core.ImageIo
import io.github.merry8989uk.photoveil.core.ShieldEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class InspectViewModel(app: Application) : AndroidViewModel(app) {

    private val context = app.applicationContext

    private var originalUri: Uri? = null
    private var candidateUri: Uri? = null

    var originalBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var candidateBitmap by mutableStateOf<Bitmap?>(null)
        private set
    var originalName by mutableStateOf<String?>(null)
        private set
    var candidateName by mutableStateOf<String?>(null)
        private set
    var report by mutableStateOf<FileReport?>(null)
        private set
    var comparison by mutableStateOf<CompareResult?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun pickOriginal(uri: Uri) {
        originalUri = uri
        comparison = null
        message = null
        viewModelScope.launch {
            val bitmap = withContext(Dispatchers.Default) {
                ImageIo.decode(context, uri, 4096)
            }
            originalBitmap = bitmap
            originalName = uri.lastPathSegment?.substringAfterLast('/') ?: "original"
        }
    }

    fun pickCandidate(uri: Uri) {
        candidateUri = uri
        comparison = null
        message = null
        busy = true
        viewModelScope.launch {
            val payload = withContext(Dispatchers.Default) {
                val bitmap = ImageIo.decode(context, uri, 4096) ?: return@withContext null
                val floor = ShieldEngine.noiseFloor(bitmap)
                val (exif, gps) = withContext(Dispatchers.IO) { ImageIo.readExif(uri, context) }
                Triple(bitmap, floor, exif to gps)
            }
            if (payload == null) {
                message = "That file could not be read as an image."
                busy = false
                return@launch
            }
            val (bitmap, floor, exif) = payload
            candidateBitmap = bitmap
            candidateName = uri.lastPathSegment?.substringAfterLast('/') ?: "file"
            report = FileReport(
                width = bitmap.width,
                height = bitmap.height,
                noiseFloor = floor,
                hasExif = exif.first,
                hasGps = exif.second,
            )
            busy = false
        }
    }

    fun compare() {
        val a = originalBitmap
        val b = candidateBitmap
        if (a == null || b == null) {
            message = "Pick both files first."
            return
        }
        if (a.width != b.width || a.height != b.height) {
            comparison = null
            message = "These files are different sizes (${a.width}×${a.height} vs " +
                "${b.width}×${b.height}), so a pixel-by-pixel comparison is not " +
                "possible. A size change on its own means the image was resized " +
                "or cropped — and that destroys the shield."
            return
        }
        message = null
        busy = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) { ShieldEngine.compare(a, b) }
            comparison = result
            busy = false
        }
    }

    fun clear() {
        originalUri = null
        candidateUri = null
        originalBitmap = null
        candidateBitmap = null
        originalName = null
        candidateName = null
        report = null
        comparison = null
        message = null
    }
}
