package io.github.merry8989uk.photoveil.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.merry8989uk.photoveil.BuildConfig
import io.github.merry8989uk.photoveil.updater.UpdateUiState
import io.github.merry8989uk.photoveil.updater.UpdateViewModel

/**
 * The About section — always last in Settings. Developer profile, contact
 * email, the follow-on-Instagram call to action, and the version with the
 * in-app update control.
 */
@Composable
fun AboutSection(updater: UpdateViewModel = viewModel()) {
    val context = LocalContext.current
    val version = BuildConfig.VERSION_NAME

    Text("About", style = MaterialTheme.typography.titleMedium)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = Contact.INITIALS,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(Contact.DEVELOPER_NAME, style = MaterialTheme.typography.titleMedium)
                Text(
                    Contact.DEVELOPER_TAGLINE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    Text("Contact", style = MaterialTheme.typography.labelLarge)
    Text(
        text = Contact.EMAIL,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
        modifier = Modifier.clickable { Contact.sendEmail(context) },
    )
    Text(
        "Bugs, feedback, feature requests — mail goes straight to the developer.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Button(
        onClick = { Contact.openInstagram(context) },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Follow @${Contact.INSTAGRAM_HANDLE} on Instagram")
    }
    Text(
        "New builds and behind-the-scenes go up there first. Follow and send a DM.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(
            onClick = { Contact.openUrl(context, Contact.REPO_URL) },
            modifier = Modifier.weight(1f),
        ) { Text("Source code") }
        OutlinedButton(
            onClick = { Contact.openUrl(context, Contact.SITE_URL) },
            modifier = Modifier.weight(1f),
        ) { Text("Website") }
    }

    HorizontalDivider()

    Text("Version $version", style = MaterialTheme.typography.bodySmall)

    val state = updater.state
    when (state) {
        UpdateUiState.Checking -> Text(
            "Checking for updates…",
            style = MaterialTheme.typography.bodySmall,
        )
        UpdateUiState.UpToDate -> Text(
            "You're on the latest version.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary,
        )
        is UpdateUiState.Available -> {
            Text(
                "Version ${state.info.latestVersion} is available.",
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = { updater.install(state.info) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Update now") }
        }
        UpdateUiState.Downloading -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp))
            Text("Downloading update…", style = MaterialTheme.typography.bodySmall)
        }
        is UpdateUiState.Failed -> Text(
            "Update failed: ${state.message}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        UpdateUiState.Idle -> Unit
    }

    OutlinedButton(
        onClick = { updater.check(version) },
        enabled = state !is UpdateUiState.Checking && state !is UpdateUiState.Downloading,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text("Check for updates")
    }
}
