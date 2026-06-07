package com.hayat

import android.content.Context
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
import androidx.room.Room
import com.hayat.data.*
import com.hayat.services.*
import com.hayat.ui.screens.ChatScreen
import com.hayat.ui.screens.ErrorScreen
import com.hayat.ui.screens.SettingsScreen
import com.hayat.ui.theme.HayatTheme
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val appDatabase = AppDatabase.getDatabase(applicationContext)
        val medicalChunkDao = appDatabase.medicalChunkDao()

        setContent {
            HayatApp(
                medicalChunkDao = medicalChunkDao
            )
        }
    }
}

@Composable
fun HayatApp(
    medicalChunkDao: MedicalChunkDao
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }
    var assetsMissing by remember { mutableStateOf(false) }
    val context = remember { androidx.compose.ui.platform.LocalContext.current }
    val hayatFolder = remember {
        File(Environment.getExternalStorageDirectory().absolutePath, "Hayat")
    }

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

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var modelState by remember { mutableStateOf<ModelState>(ModelState.Loading) }

    val embeddingModelFile = remember { File(Environment.getExternalStorageDirectory(), "Hayat/e5-small.onnx") }
    val modelFile = remember { File(Environment.getExternalStorageDirectory(), "Hayat/model.gguf") }

    var vectorSearchEngine by remember { mutableStateOf<VectorSearchEngine?>(null) }
    var inferenceService by remember { mutableStateOf<ModelInferenceService?>(null) }
    var embeddingService by remember { mutableStateOf<EmbeddingService?>(null) }

    LaunchedEffect(Unit) {
        launch {
            try {
                embeddingService = EmbeddingService(embeddingModelFile)
                vectorSearchEngine = VectorSearchEngine(medicalChunkDao)

                inferenceService = ModelInferenceService(
                    modelFile = modelFile,
                    ragEngine = { query ->
                        try {
                            val embedding = embeddingService?.generateEmbedding(query)
                            if (embedding != null) {
                                val chunks = vectorSearchEngine?.findRelevantChunks(embedding) ?: emptyList()
                                if (chunks.isNotEmpty()) {
                                    chunks
                                } else {
                                    emptyList()
                                }
                            } else {
                                emptyList()
                            }
                        } catch (e: Exception) {
                            emptyList()
                        }
                    },
                    onStateChange = { state ->
                        modelState = state
                        when (state) {
                            is ModelState.Ready -> {
                                isLoading = false
                                errorMessage = null
                            }
                            is ModelState.Error -> {
                                isLoading = false
                                errorMessage = state.message
                            }
                            ModelState.Loading -> {
                                isLoading = true
                                errorMessage = null
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                errorMessage = e.message
                modelState = ModelState.Error(e.message ?: "Initialization failed")
            }
        }
    }

    val sendMessage: (String) -> Unit = { text ->
        messages.add(ChatMessage(text, ChatRole.USER))
        isLoading = true
        errorMessage = null

        val scope = rememberCoroutineScope()

        scope.launch {
            try {
                inferenceService?.generateResponse(text)?.collect { partialResponse ->
                    if (messages.isNotEmpty() && messages.last().role == ChatRole.ASSISTANT) {
                        messages[messages.lastIndex] = ChatMessage(partialResponse, ChatRole.ASSISTANT)
                    } else {
                        messages.add(ChatMessage(partialResponse, ChatRole.ASSISTANT))
                    }
                }
            } catch (e: Exception) {
                messages.add(ChatMessage("عذراً، حدث خطأ أثناء معالجة طلبك.", ChatRole.ASSISTANT))
            } finally {
                isLoading = false
            }
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
                    ChatScreen(
                        messages = messages,
                        onSendMessage = sendMessage,
                        isLoading = isLoading,
                        errorMessage = errorMessage,
                        onRetry = {
                            errorMessage = null
                        },
                        onClearError = {
                            errorMessage = null
                        }
                    )

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
