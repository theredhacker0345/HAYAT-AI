package com.hayat.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.hayat.assets.AssetManager
import com.hayat.assets.AssetManager.AssetStatus
import java.util.Locale

@Composable
fun SetupScreen(
    assetStatuses: List<AssetStatus>,
    hayatFolderPath: String = AssetManager.HAYAT_FOLDER_PATH,
    onCheckAgain: () -> Unit = {},
    onCopyBundledAssets: () -> Unit = {},
    onAssetsReady: () -> Unit = {}
) {
    val context = LocalContext.current
    var showGuide by remember { mutableStateOf(false) }
    var copyResult by remember { mutableStateOf<String?>(null) }
    val ready = assetStatuses.isNotEmpty() && assetStatuses.all { it.isPresent }
    val hasBundledAssets = remember(assetStatuses) {
        AssetManager.hasBundledAssets(context)
    }

    if (ready) {
        LaunchedEffect(assetStatuses) {
            onAssetsReady()
        }
    }

    if (showGuide) {
        SetupGuideDialog(onDismissRequest = { showGuide = false })
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(24.dp),
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Hayat AI Setup",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    fontWeight = FontWeight.Bold
                )
            }

            item {
                Text(
                    text = "Required assets must be available before the app can run. The screen shows each file separately so partial installs are handled safely.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            item {
                AssetPathCard(path = hayatFolderPath)
            }

            item {
                AssetList(assetStatuses = assetStatuses)
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onCheckAgain,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Check Again")
                    }

                    OutlinedButton(
                        onClick = { showGuide = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Setup Guide")
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        copyResult = null
                        onCopyBundledAssets()
                        copyResult = "Copy finished. Use Check Again if any file is still missing."
                    },
                    enabled = hasBundledAssets,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Copy Bundled Assets")
                }
            }

            item {
                copyResult?.let { result ->
                    Text(
                        text = result,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            item {
                if (!hasBundledAssets) {
                    Text(
                        text = "No required assets were found inside this APK. Add the files manually to the folder above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetPathCard(path: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Expected folder",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = path,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AssetList(assetStatuses: List<AssetStatus>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Required files",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (assetStatuses.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                assetStatuses.forEach { status ->
                    AssetRow(status = status)
                }
            }
        }
    }
}

@Composable
private fun AssetRow(status: AssetStatus) {
    val stateColor = when (status.state) {
        AssetManager.State.PRESENT -> Color(0xFF4CAF50)
        AssetManager.State.SIZE_MISMATCH -> Color(0xFFFFC107)
        AssetManager.State.MISSING -> MaterialTheme.colorScheme.error
    }
    val icon = when (status.state) {
        AssetManager.State.PRESENT -> Icons.Default.CheckCircle
        AssetManager.State.SIZE_MISMATCH -> Icons.Default.Warning
        AssetManager.State.MISSING -> Icons.Default.Error
    }
    val stateText = when (status.state) {
        AssetManager.State.PRESENT -> "Present"
        AssetManager.State.SIZE_MISMATCH -> "Unexpected size"
        AssetManager.State.MISSING -> "Missing"
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = stateText,
            tint = stateColor,
            modifier = Modifier.size(24.dp)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = status.fileName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Expected: ${status.expectedSizeBytes.formatSize()} • Present: ${status.presentSizeBytes.formatSize()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = stateText,
            style = MaterialTheme.typography.labelLarge,
            color = stateColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun SetupGuideDialog(onDismissRequest: () -> Unit) {
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Setup Guide",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "Place these files in /sdcard/Hayat/ before using Hayat AI:",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                listOf("medical.db", "e5-small.onnx", "model.gguf").forEach { fileName ->
                    Text(
                        text = "• $fileName",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "If this APK includes bundled assets, use Copy Bundled Assets. Otherwise copy the files manually from the approved asset package.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Button(
                    onClick = onDismissRequest,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text("Done")
                }
            }
        }
    }
}

private fun Long?.formatSize(): String {
    val bytes = this ?: return "Unknown"
    return when {
        bytes >= 1024L * 1024L * 1024L -> String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}
