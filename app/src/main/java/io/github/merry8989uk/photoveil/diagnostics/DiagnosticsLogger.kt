package io.github.merry8989uk.photoveil.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Collects app logs into a single rolling file. Collection is ON by default,
 * so a problem can be diagnosed without the user reproducing it first.
 */
object DiagnosticsLogger {

    private const val MAX_BYTES = 1_000_000L
    private const val DIR = "logs"
    private const val NAME = "app.log"

    private val lock = Any()
    private val timeFmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

    private var logFile: File? = null

    @Volatile
    var enabled: Boolean = true
        private set

    fun init(context: Context) {
        synchronized(lock) {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            logFile = File(dir, NAME)
        }
    }

    fun setEnabled(value: Boolean) {
        if (value != enabled) {
            log("Diagnostics", if (value) "Log collection enabled" else "Log collection disabled")
        }
        enabled = value
    }

    fun log(tag: String, message: String) {
        if (!enabled) return
        val file = logFile ?: return
        val line = "${timeFmt.format(Date())} [$tag] $message\n"
        synchronized(lock) {
            runCatching {
                if (file.length() > MAX_BYTES) rotate(file)
                file.appendText(line)
            }
        }
    }

    fun readAll(): String = synchronized(lock) {
        val file = logFile ?: return ""
        if (file.exists()) file.readText() else ""
    }

    fun sizeBytes(): Long = synchronized(lock) {
        logFile?.takeIf { it.exists() }?.length() ?: 0L
    }

    fun clear() = synchronized(lock) {
        runCatching { logFile?.writeText("") }
        Unit
    }

    /** Records uncaught crashes, then defers to the previous handler. */
    fun installCrashHandler() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            log("CRASH", "${error.javaClass.name}: ${error.message}\n${error.stackTraceToString()}")
            previous?.uncaughtException(thread, error)
        }
    }

    private fun rotate(file: File) {
        val old = File(file.parentFile, "$NAME.1")
        if (old.exists()) old.delete()
        file.renameTo(old)
        file.createNewFile()
    }
}
