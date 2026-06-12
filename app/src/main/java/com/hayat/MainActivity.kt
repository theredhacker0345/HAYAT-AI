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
import androidx.lifecycle.lifecycleScope
import com.hayat.data.ChatMessage
import com.hayat.data.ChatRole
import com.hayat.services.ModelInferenceService
import com.hayat.ui.screens.ChatScreen
import com.hayat.ui.screens.ErrorScreen
import com.hayat.ui.screens.SettingsScreen
import com.hayat.ui.theme.HayatTheme
import java.io.File
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            HayatApp(contentResolver = contentResolver)
        }
    }
}

@Composable
fun HayatApp(contentResolver: android.content.ContentResolver) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }
    var assetsMissing by remember { mutableStateOf(false) }
    val hayatFolder = File(Environment.getExternalStorageDirectory(), "Hayat")
    val modelInferenceService = remember {
        ModelInferenceService(contentResolver = contentResolver)
    }

    LaunchedEffect(Unit) {
        modelInferenceService.loadModel()
    }

    // Check for assets on startup
    LaunchedEffect(Unit) {
        val modelFile = File(hayatFolder, "model.gguf")
        val embeddingModelFile = File(hayatFolder, "e5-small.onnx")
        val dbFile = File(hayatFolder, "medical.db")

        if (!hayatFolder.exists() || !modelFile.exists() || !embeddingModelFile.exists() || !dbFile.exists()) {
            assetsMissing = true
        }
    }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var modelPath by remember { mutableStateOf(File(hayatFolder, "model.gguf").absolutePath) }
    var dbPath by remember { mutableStateOf(File(hayatFolder, "medical.db").absolutePath) }

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
                        Screen.Chat -> ChatScreen(
                            messages = messages,
                            onSendMessage = { text ->
                                messages.add(ChatMessage(text, ChatRole.USER))
                                messages.add(ChatMessage("Generating response...", ChatRole.ASSISTANT))
                                lifecycleScope.launch {
                                    modelInferenceService.generateResponse(text).collect { token ->
                                        val assistantIndex = messages.indexOfLast { message ->
                                            message.role == ChatRole.ASSISTANT && message.content.startsWith("Generating response...")
                                        }
                                        if (assistantIndex >= 0) {
                                            val prefixRemoved = messages[assistantIndex].content.removePrefix("Generating response...")
                                            messages[assistantIndex] = ChatMessage(prefixRemoved + token, ChatRole.ASSISTANT)
                                        } else {
                                            messages.add(ChatMessage(token, ChatRole.ASSISTANT))
                                        }
                                    }
                                }
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

                    // Simple floating button to switch screens for demonstration
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
