package com.hayat

import android.os.Bundle
import android.os.Environment
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hayat.ui.screens.ChatScreen
import com.hayat.ui.screens.ErrorScreen
import com.hayat.ui.screens.SettingsScreen
import com.hayat.ui.theme.HayatTheme
import com.hayat.viewmodel.ChatViewModel
import java.io.File

class MainActivity : ComponentActivity() {
    private lateinit var chatViewModel: ChatViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        chatViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

        setContent {
            HayatApp(chatViewModel)
        }
    }
}

@Composable
fun HayatApp(viewModel: ChatViewModel) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }
    var assetsMissing by remember { mutableStateOf(false) }
    val hayatFolder = File(Environment.getExternalStorageDirectory(), "Hayat")

    LaunchedEffect(Unit) {
        val modelFile = File(hayatFolder, "model.gguf")
        val embeddingModelFile = File(hayatFolder, "e5-small.onnx")
        val dbFile = File(hayatFolder, "medical.db")

        if (!hayatFolder.exists() || !modelFile.exists() || !embeddingModelFile.exists() || !dbFile.exists()) {
            assetsMissing = true
        } else {
            viewModel.loadModel()
        }
    }

    HayatTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (assetsMissing) {
                ErrorScreen(missingPath = hayatFolder.absolutePath)
            } else {
                Box {
                    when (currentScreen) {
                        Screen.Chat -> ChatScreen(viewModel = viewModel)
                        Screen.Settings -> SettingsScreen(
                            modelPath = ModelInferenceService.DEFAULT_MODEL_PATH,
                            dbPath = "/sdcard/Hayat/medical.db",
                            onPathsChanged = { _, _ ->
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

sealed class Screen {
    object Chat : Screen()
    object Settings : Screen()
}