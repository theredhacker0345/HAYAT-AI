package com.hayat.ui.viewmodel

import android.content.Context
import android.os.Environment
import android.util.Log
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hayat.data.AppDatabase
import com.hayat.data.ChatMessage
import com.hayat.data.ChatRole
import com.hayat.data.MedicalChunk
import com.hayat.data.VectorSearchEngine
import com.hayat.services.EmbeddingService
import com.hayat.services.ModelInferenceService
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

class ChatViewModel(
    private val context: Context
) : ViewModel() {

    private val TAG = "ChatViewModel"
    private val hayatFolder = File(Environment.getExternalStorageDirectory(), "Hayat")

    private val _messages = mutableStateListOf<ChatMessage>()
    val messages: List<ChatMessage> = _messages

    private val _isLoading: MutableState<Boolean> = mutableStateOf(false)
    val isLoading: Boolean get() = _isLoading.value

    private val _loadingMessage: MutableState<String> = mutableStateOf("")
    val loadingMessage: String get() = _loadingMessage.value

    private var embeddingService: EmbeddingService? = null
    private var modelInferenceService: ModelInferenceService? = null
    private var vectorSearchEngine: VectorSearchEngine? = null
    private var database: AppDatabase? = null

    private var currentSource: String? = null
    private val DISCLAIMER = "⚠️ This is AI-assisted guidance, not a substitute for professional medical advice."
    private var modelPath: String = ""
    private var dbPath: String = ""
    
    // Configurable confidence threshold for RAG retrieval
    // Can be adjusted during red-teaming to balance precision vs. recall
    private var confidenceThreshold: Float = VectorSearchEngine.DEFAULT_CONFIDENCE_THRESHOLD

    init {
        modelPath = File(hayatFolder, "model.gguf").absolutePath
        dbPath = File(hayatFolder, "medical.db").absolutePath
    }
    
    /**
     * Update the RAG confidence threshold at runtime.
     * Lower values (e.g., 0.5) return more results but may include less relevant matches.
     * Higher values (e.g., 0.8) are more strict but may miss relevant guidelines.
     * 
     * @param threshold Confidence threshold between 0.0 and 1.0
     */
    fun setConfidenceThreshold(threshold: Float) {
        require(threshold in 0.0f..1.0f) { "Confidence threshold must be between 0.0 and 1.0" }
        confidenceThreshold = threshold
        Log.i(TAG, "Confidence threshold updated to: $threshold")
        
        // Reinitialize vector search engine with new threshold
        database?.medicalChunkDao()?.let { dao ->
            vectorSearchEngine = VectorSearchEngine(dao, confidenceThreshold)
        }
    }
    
    fun getConfidenceThreshold(): Float = confidenceThreshold

    fun getModelPath(): String = modelPath
    fun getDbPath(): String = dbPath

    fun initialize() {
        viewModelScope.launch {
            try {
                val modelFile = File(hayatFolder, "model.gguf")
                val embeddingModelFile = File(hayatFolder, "e5-small.onnx")
                val dbFile = File(hayatFolder, "medical.db")

                if (modelFile.exists()) {
                    modelInferenceService = ModelInferenceService(context.contentResolver)
                    modelInferenceService?.loadModel()
                }

                if (embeddingModelFile.exists()) {
                    embeddingService = EmbeddingService(context, embeddingModelFile)
                }

                if (dbFile.exists()) {
                    database = AppDatabase.getDatabase(context)
                    val dao = database?.medicalChunkDao()
                    if (dao != null) {
                        vectorSearchEngine = VectorSearchEngine(dao)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing services: ${e.message}")
            }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank()) return

        _messages.add(ChatMessage(text, ChatRole.USER))
        _messages.add(ChatMessage("", ChatRole.ASSISTANT))
        _isLoading.value = true
        _loadingMessage.value = "Searching medical guidelines..."

        viewModelScope.launch {
            processMessageWithRAG(text)
        }
    }

    private suspend fun processMessageWithRAG(query: String) {
        _loadingMessage.value = "Generating embedding..."

        // Use embedQuery() which automatically adds the required "query:" prefix
        val embedding = try {
            embeddingService?.embedQuery(query)
        } catch (e: Exception) {
            Log.e(TAG, "Embedding failed: ${e.message}")
            null
        }

        if (embedding == null) {
            updateAssistantMessage("Unable to generate embedding. Please ensure the embedding model is available.")
            return
        }

        _loadingMessage.value = "Searching medical guidelines..."

        val searchResults = try {
            vectorSearchEngine?.search(embedding, topK = 5) ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Search failed: ${e.message}")
            emptyList()
        }

        val topResult = searchResults.firstOrNull()
        val contextFromChunk = topResult?.chunk
        val similarity = topResult?.similarity

        Log.d(TAG, "Top match: title=${contextFromChunk?.protocol_title}, similarity=$similarity, threshold=$confidenceThreshold")

        if (contextFromChunk == null || similarity == null) {
            updateAssistantMessage(vectorSearchEngine?.getRefusalMessage() ?: "I couldn't find relevant medical guidelines. Please consult a healthcare professional.")
            return
        }

        currentSource = contextFromChunk.protocol_title

        _loadingMessage.value = "Generating response..."

        val prompt = buildPrompt(contextFromChunk, query)
        Log.d(TAG, "RAG Prompt constructed with context from: $currentSource")

        val responseFlow = modelInferenceService?.generateResponse(prompt)

        if (responseFlow == null) {
            updateAssistantMessage("Model not available. Please place model.gguf in /sdcard/Hayat/")
            return
        }

        try {
            responseFlow.collect { token ->
                val lastIndex = _messages.indexOfLast { it.role == ChatRole.ASSISTANT }
                if (lastIndex >= 0) {
                    val currentContent = _messages[lastIndex].content
                    _messages[lastIndex] = ChatMessage(currentContent + token, ChatRole.ASSISTANT, currentSource)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generation failed: ${e.message}")
            updateAssistantMessageWithError("Failed to generate response. Please try again.")
            return
        }

        finalizeAssistantMessage()
    }

    private fun finalizeAssistantMessage() {
        val lastIndex = _messages.indexOfLast { it.role == ChatRole.ASSISTANT }
        if (lastIndex >= 0) {
            val finalContent = _messages[lastIndex].content
            _messages[lastIndex] = ChatMessage(finalContent + "\n\n$DISCLAIMER", ChatRole.ASSISTANT, currentSource)
        }
        _isLoading.value = false
        _loadingMessage.value = ""
    }

    private fun updateAssistantMessage(content: String) {
        val lastIndex = _messages.indexOfLast { it.role == ChatRole.ASSISTANT }
        if (lastIndex >= 0) {
            _messages[lastIndex] = ChatMessage(content + "\n\n$DISCLAIMER", ChatRole.ASSISTANT)
        }
        _isLoading.value = false
        _loadingMessage.value = ""
    }

    private fun updateAssistantMessageWithError(content: String) {
        val lastIndex = _messages.indexOfLast { it.role == ChatRole.ASSISTANT }
        if (lastIndex >= 0) {
            _messages[lastIndex] = ChatMessage(content + "\n\n$DISCLAIMER", ChatRole.ASSISTANT)
        }
        _isLoading.value = false
        _loadingMessage.value = ""
    }

    private fun buildPrompt(contextChunk: MedicalChunk, query: String): String {
        return """Based on the medical protocol context below, answer the user's question:

Context: ${contextChunk.protocol_title}: ${contextChunk.content}

Question: $query

Answer:"""
    }

    fun clearMessages() {
        _messages.clear()
    }

    override fun onCleared() {
        super.onCleared()
        embeddingService?.close()
        modelInferenceService?.close()
    }
}