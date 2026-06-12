package com.hayat.services

import android.content.ContentResolver
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.nehuatl.llamacpp.LlamaAndroid
import org.nehuatl.llamacpp.LlamaHelper
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

class ModelInferenceService(
    private val contentResolver: ContentResolver,
    private val modelFile: File = File(Environment.getExternalStorageDirectory(), "Hayat/model.gguf")
) : AutoCloseable {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sharedFlow = MutableSharedFlow<LlamaHelper.LLMEvent>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    private val llamaHelper = LlamaHelper(
        contentResolver = contentResolver,
        scope = scope,
        sharedFlow = sharedFlow
    )

    private val loadLock = Any()
    private var contextId: Int? = null
    private var loadError: String? = null
    private val generationInProgress = AtomicBoolean(false)

    val isModelLoaded: Boolean
        get() = contextId != null

    val lastError: String?
        get() = loadError

    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        synchronized(loadLock) {
            if (contextId != null) {
                return@synchronized true
            }

            if (!modelFile.exists()) {
                loadError = missingModelMessage()
                Log.w(TAG, loadError)
                return@synchronized false
            }

            if (modelFile.length() <= 0L) {
                loadError = "Model file is empty at ${modelFile.absolutePath}. Add a valid GGUF model to /sdcard/Hayat/."
                Log.w(TAG, loadError)
                return@synchronized false
            }

            val loadedId = CompletableDeferred<Int>()
            val loadFailure = CompletableDeferred<Throwable?>()
            val eventJob = scope.launch {
                val event = withTimeoutOrNull(LOAD_TIMEOUT_MS) {
                    sharedFlow.first { it is LlamaHelper.LLMEvent.Loaded || it is LlamaHelper.LLMEvent.Error }
                }
                if (event is LlamaHelper.LLMEvent.Error && !loadedId.isCompleted && !loadFailure.isCompleted) {
                    loadFailure.complete(
                        IllegalStateException(event.message ?: "Model loading failed")
                    )
                }
            }

            try {
                llamaHelper.load(
                    path = modelUri(),
                    contextLength = CONTEXT_LENGTH,
                    mmprojPath = null
                ) { id ->
                    if (!loadedId.isCompleted && !loadFailure.isCompleted) {
                        loadedId.complete(id.toInt())
                    }
                }
            } catch (e: Exception) {
                if (!loadedId.isCompleted && !loadFailure.isCompleted) {
                    loadFailure.complete(e)
                }
            }

            val id = withTimeoutOrNull(LOAD_TIMEOUT_MS) { loadedId.await() }
            val failure = if (loadFailure.isCompleted) loadFailure.getCompleted() else null
            eventJob.cancel()

            when {
                failure != null -> {
                    loadError = "Failed to load GGUF model: ${failure.message}"
                    Log.w(TAG, loadError, failure)
                    false
                }
                id == null -> {
                    loadError = "Model loading timed out. Check that ${modelFile.absolutePath} is a valid GGUF file."
                    Log.w(TAG, loadError)
                    false
                }
                else -> {
                    contextId = id
                    loadError = null
                    Log.d(TAG, "GGUF model loaded from ${modelFile.absolutePath}")
                    true
                }
            }
        }
    }

    fun generateResponse(prompt: String): Flow<String> = flow {
        if (prompt.isBlank()) {
            emit("Please provide a message for the local medical assistant to answer.")
            return@flow
        }

        if (!loadModel()) {
            emit(loadError ?: missingModelMessage())
            return@flow
        }

        val id = contextId ?: run {
            emit("Model is not loaded. Add model.gguf to /sdcard/Hayat/ and reopen the app.")
            return@flow
        }

        if (!generationInProgress.compareAndSet(false, true)) {
            emit("A response is already being generated. Please wait.")
            return@flow
        }

        val generatedText = StringBuilder()
        try {
            val tokenJob = scope.launch {
                sharedFlow
                    .filterIsInstance<LlamaHelper.LLMEvent.Ongoing>()
                    .collect { event ->
                        if (event.word.isNotBlank()) {
                            generatedText.append(event.word)
                            emit(event.word)
                        }
                    }
            }

            val result = try {
                withContext(Dispatchers.IO) {
                    llamaAndroid().launchCompletion(id, completionParams(prompt.trim()))
                }
            } finally {
                tokenJob.cancel()
            }

            val error = result?.let { extractError(it) }
            if (!error.isNullOrBlank()) {
                emit("LLM inference failed: $error")
                return@flow
            }

            if (generatedText.isEmpty()) {
                val finalText = result?.let { extractFinalText(it) }
                if (!finalText.isNullOrBlank()) {
                    emit(finalText)
                } else {
                    emit("No response generated by the local model. Check that the GGUF file is valid and compatible.")
                }
            }
        } catch (e: CancellationException) {
            llamaHelper.stopPrediction()
            throw e
        } catch (e: Exception) {
            llamaHelper.stopPrediction()
            emit("LLM inference failed: ${e.message ?: "unknown error"}")
            Log.w(TAG, "Generation failed", e)
        } finally {
            generationInProgress.set(false)
        }
    }.flowOn(Dispatchers.IO)

    private fun llamaAndroid(): LlamaAndroid {
        val method = LlamaHelper::class.java.getDeclaredMethod("getLlama")
        method.isAccessible = true
        return method.invoke(llamaHelper) as LlamaAndroid
    }

    private fun completionParams(prompt: String): Map<String, Any> = mapOf(
        "prompt" to prompt,
        "temperature" to TEMPERATURE,
        "n_predict" to MAX_TOKENS,
        "top_p" to TOP_P,
        "top_k" to TOP_K,
        "emit_partial_completion" to true
    )

    private fun extractFinalText(result: Map<String, Any>): String? =
        (result["completion_text"] as? String)
            ?: (result["completion"] as? String)
            ?: (result["content"] as? String)
            ?: (result["text"] as? String)
            ?: (result["response"] as? String)
            ?: (result["generated_text"] as? String)

    private fun extractError(result: Map<String, Any>): String? =
        (result["error"] as? String)
            ?: (result["message"] as? String)

    private fun modelUri(): String = Uri.fromFile(modelFile).toString()

    private fun missingModelMessage(): String =
        "Model file not found at ${modelFile.absolutePath}. Add model.gguf to /sdcard/Hayat/ before using inference."

    override fun close() {
        llamaHelper.abort()
        llamaHelper.release()
        scope.cancel()
    }

    companion object {
        private const val TAG = "ModelInferenceService"
        private const val CONTEXT_LENGTH = 2048
        private const val MAX_TOKENS = 512
        private const val TEMPERATURE = 0.7
        private const val TOP_P = 0.9
        private const val TOP_K = 40
        private const val LOAD_TIMEOUT_MS = 120_000L
    }
}
