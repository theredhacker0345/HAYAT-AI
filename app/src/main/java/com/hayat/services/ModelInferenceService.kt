package com.hayat.services

import android.content.Context
import android.util.Log
import com.hayat.ui.screens.MedicalPromptBuilder
import io.github.ljcamargo.llama.LlamaHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File

class ModelInferenceService private constructor(
    private val context: Context,
    private val contentResolver: android.content.ContentResolver
) : AutoCloseable {
    private val TAG = "ModelInferenceService"

    private val _state = MutableStateFlow<ModelState>(ModelState.Unloaded)
    val state: StateFlow<ModelState> = _state.asStateFlow()

    private val _tokens = MutableStateFlow<String>("")
    val responseText: StateFlow<String> = _tokens.asStateFlow()

    private val inferenceParams = InferenceParams(
        temperature = 0.7f,
        maxTokens = 512,
        topP = 0.9f
    )

    private val llamaDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val llamaScope = CoroutineScope(llamaDispatcher + SupervisorJob())

    private val _llmEventFlow = MutableSharedFlow<LlamaHelper.LLMEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    private var llamaHelper: LlamaHelper? = null
    private var isModelLoaded = false

    companion object {
        @Volatile
        private var INSTANCE: ModelInferenceService? = null

        fun getInstance(context: Context): ModelInferenceService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: ModelInferenceService(context, context.contentResolver).also { INSTANCE = it }
            }

        const val DEFAULT_MODEL_PATH = "/sdcard/Hayat/model.gguf"
    }

    suspend fun loadModel(modelPath: String = DEFAULT_MODEL_PATH): Result<Unit> = withContext(llamaDispatcher) {
        try {
            if (isModelLoaded) {
                Log.d(TAG, "Model already loaded")
                return@withContext Result.success(Unit)
            }

            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                val error = ModelLoadException("Model file not found: $modelPath")
                Log.e(TAG, error.message)
                _state.value = ModelState.Error(error)
                return@withContext Result.failure(error)
            }

            if (!modelFile.isFile) {
                val error = ModelLoadException("Not a valid file: $modelPath")
                Log.e(TAG, error.message)
                _state.value = ModelState.Error(error)
                return@withContext Result.failure(error)
            }

            if (!modelFile.canRead()) {
                val error = ModelLoadException("Cannot read model file: $modelPath")
                Log.e(TAG, error.message)
                _state.value = ModelState.Error(error)
                return@withContext Result.failure(error)
            }

            _state.value = ModelState.Loading
            Log.d(TAG, "Loading model from: $modelPath")

            llamaHelper = LlamaHelper(contentResolver, llamaScope, _llmEventFlow)

            val modelUri = android.net.Uri.fromFile(modelFile).toString()
            llamaHelper?.load(
                path = modelUri,
                contextLength = 2048,
                threads = 4
            ) { success ->
                if (success) {
                    Log.d(TAG, "Model loaded successfully")
                } else {
                    Log.e(TAG, "Model failed to load")
                }
            }

            isModelLoaded = true
            _state.value = ModelState.Ready

            Result.success(Unit)
        } catch (e: OutOfMemoryError) {
            val error = ModelLoadException("OOM loading model. Try a smaller quantization.", e)
            Log.e(TAG, error.message, e)
            _state.value = ModelState.Error(error)
            Result.failure(error)
        } catch (e: Exception) {
            val error = ModelLoadException("Failed to load model: ${e.message}", e)
            Log.e(TAG, error.message, e)
            _state.value = ModelState.Error(error)
            Result.failure(error)
        }
    }

    suspend fun generateResponse(
        userQuery: String,
        contextChunks: List<com.hayat.data.MedicalChunk> = emptyList()
    ): Flow<String> = flow {
        if (!isModelLoaded) {
            throw IllegalStateException("Model not loaded. Call loadModel() first.")
        }

        try {
            _state.value = ModelState.Generating
            _tokens.value = ""

            val prompt = MedicalPromptBuilder.buildPrompt(userQuery, contextChunks)
            Log.d(TAG, "Generated prompt: ${prompt.take(100)}...")

            llamaHelper?.predict(prompt)

            _llmEventFlow.collect { event ->
                when (event) {
                    is LlamaHelper.LLMEvent.Ongoing -> {
                        _tokens.value += event.word
                        emit(event.word)
                    }
                    is LlamaHelper.LLMEvent.Done -> {
                        _state.value = ModelState.Ready
                        return@collect
                    }
                    is LlamaHelper.LLMEvent.Error -> {
                        _state.value = ModelState.Error(event.exception)
                        throw event.exception
                    }
                    else -> {}
                }
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error during generation", e)
            _state.value = ModelState.Error(ModelInferenceException("Generation failed: ${e.message}", e))
            throw e
        }
    }.flowOn(Dispatchers.IO)

    fun stopGeneration() {
        llamaHelper?.stop()
    }

    override fun close() {
        try {
            llamaHelper?.cleanUp()
            isModelLoaded = false
            _state.value = ModelState.Unloaded
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}", e)
        }
    }

    fun destroy() {
        try {
            llamaHelper?.destroy()
            isModelLoaded = false
            _state.value = ModelState.Unloaded
            llamaScope.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Error during destroy: ${e.message}", e)
        }
    }
}

data class InferenceParams(
    val temperature: Float = 0.7f,
    val maxTokens: Int = 512,
    val topP: Float = 0.9f
)

sealed class ModelState {
    object Unloaded : ModelState()
    object Loading : ModelState()
    object Ready : ModelState()
    object Generating : ModelState()
    data class Error(val exception: Exception) : ModelState()
}

class ModelLoadException(message: String, cause: Throwable? = null) : Exception(message, cause)
class ModelInferenceException(message: String, cause: Throwable? = null) : Exception(message, cause)