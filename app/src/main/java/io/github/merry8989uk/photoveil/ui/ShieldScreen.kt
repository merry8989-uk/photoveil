package io.github.merry8989uk.photoveil.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.merry8989uk.photoveil.core.ShieldEngine
import io.github.merry8989uk.photoveil.core.ShieldMode

@Composable
fun ShieldScreen(vm: ShieldViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(10),
    ) { uris ->
        if (uris.isNotEmpty()) vm.onPicked(uris)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // Preview
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val bitmap = vm.preview
            if (bitmap != null) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "Selected photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "Pick a photo to shield.\nIt never leaves your phone.",
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                Text("Choose photos")
            }
            OutlinedButton(
                onClick = vm::clear,
                enabled = vm.picked.isNotEmpty() && !vm.busy,
                modifier = Modifier.weight(1f),
            ) {
                Text("Clear")
            }
        }

        Text("Shield mode", style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ShieldMode.entries.forEach { mode ->
                FilterChip(
                    selected = settings.shieldMode == mode,
                    onClick = { vm.setMode(mode) },
                    label = { Text(mode.label) },
                )
            }
        }
        Text(
            settings.shieldMode.blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        val amplitude = ShieldEngine.amplitudeFor(settings.shieldMode, settings.shieldStrength)
        Text(
            "Strength — up to %.1f/255 per pixel".format(amplitude),
            style = MaterialTheme.typography.titleSmall,
        )
        Slider(
            value = settings.shieldStrength,
            onValueChange = vm::setStrength,
            valueRange = 0.1f..1f,
        )
        Text(
            "Higher is harder to undo, and slightly easier to spot on a large " +
                "screen. 0.6 is a good default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = vm::applyShield,
            enabled = !vm.busy && vm.picked.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (vm.busy) "Shielding…" else "Apply shield")
        }

        if (vm.busy) {
            LinearProgressIndicator(
                progress = { vm.progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        vm.status?.let {
            Text(it, style = MaterialTheme.typography.bodySmall)
        }

        vm.results.forEach { item ->
            ResultCard(
                item = item,
                onShare = { vm.share(item) },
                onSaveToGallery = { vm.saveToGallery(item) },
            )
        }

        Text(
            "How it works: a band-limited, texture-masked perturbation is added " +
                "to the pixels. It targets the high-frequency detail that a " +
                "diffusion model's encoder samples hardest, so an edit drifts " +
                "away from your photo. It is a deterrent, not a guarantee — " +
                "resizing or heavy re-compression can wash it out.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultCard(
    item: ShieldedItem,
    onShare: () -> Unit,
    onSaveToGallery: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Image(
                bitmap = item.bitmap.asImageBitmap(),
                contentDescription = "Shielded photo",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(12.dp)),
            )
            Text(item.name, style = MaterialTheme.typography.titleSmall)
            StatLine("Average change", "%.2f/255".format(item.stats.meanAbsDelta))
            StatLine("Largest change", "${item.stats.maxDelta}/255")
            StatLine("Pixels touched", "%.0f%%".format(item.stats.changedPercent))
            StatLine(
                "Noise floor",
                "%.2f → %.2f".format(item.stats.noiseFloorBefore, item.stats.noiseFloorAfter),
            )
            StatLine("Took", "${item.stats.elapsedMs} ms")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onShare, modifier = Modifier.weight(1f)) { Text("Share") }
                OutlinedButton(
                    onClick = onSaveToGallery,
                    modifier = Modifier.weight(1f),
                ) { Text("Save to gallery") }
            }
        }
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}
