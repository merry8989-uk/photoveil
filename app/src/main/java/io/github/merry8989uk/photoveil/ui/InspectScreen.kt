package io.github.merry8989uk.photoveil.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun InspectScreen(vm: InspectViewModel = viewModel()) {
    val pickImage = ActivityResultContracts.PickVisualMedia()
    val originalPicker = rememberLauncherForActivityResult(pickImage) { uri ->
        if (uri != null) vm.pickOriginal(uri)
    }
    val candidatePicker = rememberLauncherForActivityResult(pickImage) { uri ->
        if (uri != null) vm.pickCandidate(uri)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Check a shield", style = MaterialTheme.typography.titleMedium)
        Text(
            "Pick the photo you started with, and the file you are holding now. " +
                "If the difference between them is fine, noise-like and spread " +
                "over the whole frame, the shield survived. If it is smooth or " +
                "the sizes differ, the file was edited or re-saved and the " +
                "shield is gone.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Slot(
            title = "1 · The original",
            name = vm.originalName,
            bitmap = vm.originalBitmap?.asImageBitmap(),
            onPick = {
                originalPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )
        Slot(
            title = "2 · The file to check",
            name = vm.candidateName,
            bitmap = vm.candidateBitmap?.asImageBitmap(),
            onPick = {
                candidatePicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = vm::compare,
                enabled = !vm.busy && vm.originalBitmap != null && vm.candidateBitmap != null,
                modifier = Modifier.weight(1f),
            ) { Text("Compare") }
            OutlinedButton(onClick = vm::clear, modifier = Modifier.weight(1f)) {
                Text("Clear")
            }
        }

        if (vm.busy) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp))
                Text("Measuring…", style = MaterialTheme.typography.bodySmall)
            }
        }

        vm.message?.let {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    it,
                    modifier = Modifier.padding(12.dp),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }

        vm.comparison?.let { c ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(c.verdict.title, style = MaterialTheme.typography.titleSmall)
                    Text(
                        c.verdict.detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    InfoRow("Mean difference", "%.2f/255".format(c.meanAbsDiff))
                    InfoRow("Pixels differing", "%.0f%%".format(c.changedPercent))
                    InfoRow("Fine-grained share", "%.0f%%".format(c.hfFraction * 100))
                }
            }
        }

        vm.report?.let { r ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text("File details", style = MaterialTheme.typography.titleSmall)
                    InfoRow("Dimensions", "${r.width} × ${r.height}")
                    InfoRow("Noise floor", "%.2f/255".format(r.noiseFloor))
                    InfoRow("EXIF metadata", if (r.hasExif) "present" else "stripped")
                    InfoRow("GPS location", if (r.hasGps) "present" else "none")
                }
            }
            Text(
                "The noise floor on its own is only a hint — a high-ISO photo can " +
                    "read as high as a shielded one. The comparison above is the " +
                    "answer that actually holds up.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Slot(
    title: String,
    name: String?,
    bitmap: androidx.compose.ui.graphics.ImageBitmap?,
    onPick: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = name,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(10.dp)),
                )
                Text(
                    name ?: "selected",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
                Text(if (bitmap == null) "Choose a file" else "Choose another file")
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
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
