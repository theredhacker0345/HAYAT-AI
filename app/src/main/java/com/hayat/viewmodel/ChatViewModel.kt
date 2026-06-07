package com.hayat.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hayat.data.AppDatabase
import com.hayat.data.ChatMessage
import com.hayat.data.ChatRole
import com.hayat.data.MedicalDatabaseSeeder
import com.hayat.data.VectorSearchEngine
import com.hayat.services.ModelInferenceService
import com.hayat.services.ModelState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ChatViewModel"

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val modelInferenceService: ModelInferenceService
    private val embeddingService: com.hayat.services.EmbeddingService
    private val vectorSearchEngine: VectorSearchEngine

    private var currentContextChunks: List<com.hayat.data.MedicalChunk> = emptyList()

    init {
        val context = application

        MedicalDatabaseSeeder(context).seedDatabaseIfNeeded()

        val db = AppDatabase.getDatabase(context)
        vectorSearchEngine = VectorSearchEngine(db.medicalChunkDao())

        modelInferenceService = ModelInferenceService.getInstance(context)

        val embeddingModelFile = File("/sdcard/Hayat/e5-small.onnx")
        embeddingService = com.hayat.services.EmbeddingService(embeddingModelFile)
    }

    fun loadModel() {
        viewModelScope.launch {
            modelInferenceService.loadModel()
            modelInferenceService.state.collect { state ->
                when (state) {
                    is ModelState.Loading -> _isLoading.value = true
                    is ModelState.Ready -> _isLoading.value = false
                    is ModelState.Error -> {
                        _isLoading.value = false
                        _error.value = state.exception.message
                    }
                    else -> {}
                }
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        val currentList = _messages.value.toMutableList()
        currentList.add(ChatMessage(text, ChatRole.USER))
        _messages.value = currentList
        _isLoading.value = true

        viewModelScope.launch {
            try {
                val queryEmbedding = withContext(Dispatchers.IO) {
                    embeddingService.generateEmbedding(text)
                }

                currentContextChunks = if (queryEmbedding != null) {
                    vectorSearchEngine.findRelevantChunks(queryEmbedding)
                } else {
                    emptyList()
                }

                var fullResponse = ""
                modelInferenceService.generateResponse(text, currentContextChunks).collect { token ->
                    fullResponse += token
                    val updatedList = _messages.value.toMutableList()
                    val lastIndex = updatedList.indexOfLast { it.role == ChatRole.ASSISTANT }
                    if (lastIndex >= 0) {
                        updatedList[lastIndex] = ChatMessage(fullResponse, ChatRole.ASSISTANT, currentContextChunks)
                    } else {
                        updatedList.add(ChatMessage(fullResponse, ChatRole.ASSISTANT, currentContextChunks))
                    }
                    _messages.value = updatedList
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating response", e)
                _error.value = e.message
                val updatedList = _messages.value.toMutableList()
                updatedList.add(ChatMessage("عذراً، حدث خطأ في معالجة طلبك. يرجى المحاولة مرة أخرى.", ChatRole.ASSISTANT))
                _messages.value = updatedList
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }

    fun retryLastMessage() {
        val lastUserMessage = _messages.value.reversed().find { it.role == ChatRole.USER }
        lastUserMessage?.let {
            // Remove the last assistant message if exists
            val updatedMessages = _messages.value.filter { msg -> msg.role != ChatRole.ASSISTANT }
            _messages.value = updatedMessages
            sendMessage(it.content)
        }
    }
}