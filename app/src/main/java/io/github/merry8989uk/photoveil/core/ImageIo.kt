package io.github.merry8989uk.photoveil.core

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OutputFormat(val label: String, val ext: String, val mime: String) {
    PNG("PNG — lossless", "png", "image/png"),
    JPEG("JPEG — smaller", "jpg", "image/jpeg"),
}

/**
 * Decoding, saving, sharing and the on-device vault. No network, no
 * third-party image library — BitmapFactory and MediaStore only.
 */
object ImageIo {

    /** Decodes a picked image, downsampling so the longest side is <= [maxDimension]. */
    fun decode(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
        }
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0) return null

        var sample = 1
        val longest = maxOf(width, height)
        while (longest / sample > maxDimension * 2) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = runCatching {
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        }.getOrNull() ?: return null

        return downscale(decoded, maxDimension)
    }

    fun decodeFile(file: File, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val longest = maxOf(bounds.outWidth, bounds.outHeight)
        if (longest <= 0) return null

        var sample = 1
        while (longest / sample > maxDimension * 2) sample *= 2

        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return null
        return downscale(decoded, maxDimension)
    }

    private fun downscale(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= maxDimension) return bitmap
        val scale = maxDimension.toFloat() / longest
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, w, h, true)
        if (scaled != bitmap) bitmap.recycle()
        return scaled
    }

    /** The app's own folder for shielded images. */
    fun vaultDir(context: Context): File {
        val base = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            ?: File(context.filesDir, "Pictures")
        return File(base, "PhotoVeil").apply { mkdirs() }
    }

    fun listVault(context: Context): List<File> =
        vaultDir(context).listFiles()
            ?.filter { it.isFile && (it.name.endsWith(".png") || it.name.endsWith(".jpg")) }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()

    /** Writes the shielded image into the vault and returns the file. */
    fun saveToVault(
        context: Context,
        bitmap: Bitmap,
        format: OutputFormat,
        quality: Int,
        index: Int = 0,
    ): File {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val suffix = if (index > 0) "-$index" else ""
        val file = File(vaultDir(context), "photoveil-$stamp$suffix.${format.ext}")
        FileOutputStream(file).use { out ->
            bitmap.compress(compressFormat(format), qualityFor(format, quality), out)
        }
        return file
    }

    /** Copies the shielded image into the device gallery (Android 10+). */
    fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        format: OutputFormat,
        quality: Int,
        displayName: String,
    ): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, format.mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/PhotoVeil")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = runCatching {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        }.getOrNull() ?: return null

        runCatching {
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(compressFormat(format), qualityFor(format, quality), out)
            }
            val done = ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }
            resolver.update(uri, done, null, null)
        }.onFailure {
            runCatching { resolver.delete(uri, null, null) }
            return null
        }
        return uri
    }

    /** Opens the system share sheet for a file this app owns. */
    fun shareFile(context: Context, file: File, mime: String, subject: String = file.name) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "Share shielded image"))
    }

    /** Reads EXIF/GPS presence for the inspector. */
    fun readExif(uri: Uri, context: Context): Pair<Boolean, Boolean> {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val exif = ExifInterface(input)
                val gps = exif.getLatLong() != null
                val hasAny = exif.getAttribute(ExifInterface.TAG_MAKE) != null ||
                    exif.getAttribute(ExifInterface.TAG_MODEL) != null ||
                    exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) != null ||
                    gps
                hasAny to gps
            } ?: (false to false)
        }.getOrDefault(false to false)
    }

    fun readExifFile(file: File): Pair<Boolean, Boolean> = runCatching {
        val exif = ExifInterface(file.absolutePath)
        val gps = exif.getLatLong() != null
        val hasAny = exif.getAttribute(ExifInterface.TAG_MAKE) != null ||
            exif.getAttribute(ExifInterface.TAG_MODEL) != null ||
            exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) != null ||
            gps
        hasAny to gps
    }.getOrDefault(false to false)

    fun humanSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.0f KB".format(bytes / 1024.0)
        else -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    }

    private fun compressFormat(format: OutputFormat) =
        if (format == OutputFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

    private fun qualityFor(format: OutputFormat, quality: Int) =
        if (format == OutputFormat.PNG) 100 else quality.coerceIn(40, 100)
}
