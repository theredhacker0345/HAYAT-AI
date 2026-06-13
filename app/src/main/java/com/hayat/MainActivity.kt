package com.hayat

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.hayat.assets.AssetManager
import com.hayat.assets.AssetManager.AssetStatus
import com.hayat.data.ChatMessage
import com.hayat.data.ChatRole
import com.hayat.ui.screens.ChatScreen
import com.hayat.ui.screens.SettingsScreen
import com.hayat.ui.screens.SetupScreen
import com.hayat.ui.theme.HayatTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HayatApp()
        }
    }
}

@Composable
fun HayatApp() {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }
    var assetStatuses by remember { mutableStateOf<List<AssetStatus>>(emptyList()) }

    fun refreshAssets() {
        assetStatuses = AssetManager.checkAssets(context)
    }

    LaunchedEffect(Unit) {
        refreshAssets()
    }

    val assetsReady = assetStatuses.isNotEmpty() && assetStatuses.all { it.state == AssetManager.State.PRESENT }
    val messages = remember { mutableStateListOf<ChatMessage>() }
    var modelPath by remember { mutableStateOf(AssetManager.hayatFolder().resolve("model.gguf").absolutePath) }
    var dbPath by remember { mutableStateOf(AssetManager.hayatFolder().resolve("medical.db").absolutePath) }

    HayatTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            when {
                assetStatuses.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                !assetsReady -> {
                    SetupScreen(
                        assetStatuses = assetStatuses,
                        hayatFolderPath = AssetManager.HAYAT_FOLDER_PATH,
                        onCheckAgain = { refreshAssets() },
                        onCopyBundledAssets = {
                            assetStatuses = AssetManager.copyBundledAssets(context)
                        }
                    )
                }
                else -> {
                    Box {
                        when (currentScreen) {
                            Screen.Chat -> ChatScreen(
                                messages = messages,
                                onSendMessage = { text ->
                                    messages.add(ChatMessage(text, ChatRole.USER))
                                    messages.add(ChatMessage("Checking medical guidelines for: $text", ChatRole.ASSISTANT))
                                }
                            )
                            Screen.Settings -> SettingsScreen(
                                modelPath = modelPath,
                                dbPath = dbPath,
                                onPathsChanged = { newModel, newDb ->
                                    modelPath = newModel
                                    dbPath = newDb
                                    currentScreen = Screen.Chat
                                }
                            )
                        }

                        FloatingActionButton(
                            onClick = {
                                currentScreen = if (currentScreen == Screen.Chat) Screen.Settings else Screen.Chat
                            },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp),
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Toggle Settings",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}

sealed class Screen {
    object Chat : Screen()
    object Settings : Screen()
}
