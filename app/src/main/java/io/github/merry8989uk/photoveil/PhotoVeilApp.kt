package io.github.merry8989uk.photoveil

import android.app.Application
import io.github.merry8989uk.photoveil.diagnostics.DiagnosticsLogger

/**
 * Application entry point. Starts diagnostics logging (on by default) and
 * captures crashes before the app is fully up.
 */
class PhotoVeilApp : Application() {

    override fun onCreate() {
        super.onCreate()
        DiagnosticsLogger.init(this)
        DiagnosticsLogger.installCrashHandler()
        DiagnosticsLogger.log("App", "Started version ${BuildConfig.VERSION_NAME}")
    }
}
