package io.github.merry8989uk.photoveil.settings

import android.content.Context
import android.content.Intent
import android.net.Uri

/** The developer's profile and contact details, in one place. */
object Contact {

    const val DEVELOPER_NAME = "Amit"
    const val DEVELOPER_TAGLINE = "Android apps, tools and experiments"
    const val INITIALS = "A"

    const val EMAIL = "br19shc39@gmail.com"
    const val INSTAGRAM_HANDLE = "osintgram_io"
    const val INSTAGRAM_URL = "https://instagram.com/osintgram_io"
    const val REPO_URL = "https://github.com/merry8989-uk/photoveil"
    const val SITE_URL = "https://merry8989-uk.github.io/photoveil/"

    /** Opens Instagram — the app if installed, else the browser. */
    fun openInstagram(context: Context) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(INSTAGRAM_URL)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun openUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    /** Opens the user's mail app, addressed to the developer. */
    fun sendEmail(context: Context, subject: String = "PhotoVeil feedback") {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_EMAIL, arrayOf(EMAIL))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }
}
