package com.hayat.services

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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

/**
 * Vision Inference Service using Moondream 2 for image analysis.
 * Handles multimodal inference with strict image preprocessing (384x384 downscaling).
 */
class VisionInferenceService(
    private val contentResolver: ContentResolver,
    private val modelFile: File = File(Environment.getExternalStorageDirectory(), "Hayat/moondream2.gguf"),
    private val mmprojFile: File = File(Environment.getExternalStorageDirectory(), "Hayat/moondream2-mmproj.gguf")
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

    /**
     * Load the Moondream 2 vision model with its mmproj file.
     */
    suspend fun loadModel(): Boolean = withContext(Dispatchers.IO) {
        synchronized(loadLock) {
            if (contextId != null) {
                return@synchronized true
            }

            // Validate model files
            if (!modelFile.exists()) {
                loadError = missingModelMessage()
                Log.w(TAG, loadError)
                return@synchronized false
            }

            if (!mmprojFile.exists()) {
                loadError = missingMmprojMessage()
                Log.w(TAG, loadError)
                return@synchronized false
            }

            if (modelFile.length() <= 0L || mmprojFile.length() <= 0L) {
                loadError = "Model or mmproj file is empty. Check /sdcard/Hayat/ directory."
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
                    mmprojPath = mmprojUri()
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
                    loadError = "Failed to load Moondream 2 model: ${failure.message}"
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
                    Log.d(TAG, "Moondream 2 model loaded from ${modelFile.absolutePath}")
                    true
                }
            }
        }
    }

    /**
     * Generate response from an image with optional text prompt.
     * Automatically downscales image to 384x384 before processing.
     */
    fun generateResponseFromImage(
        imageUri: Uri,
        prompt: String = "Describe this medical image in detail. Focus on visible symptoms, injuries, or conditions."
    ): Flow<String> = flow {
        if (!loadModel()) {
            emit(lastError ?: missingModelMessage())
            return@flow
        }

        val id = contextId ?: run {
            emit("Vision model is not loaded. Add moondream2.gguf and moondream2-mmproj.gguf to /sdcard/Hayat/")
            return@flow
        }

        if (!generationInProgress.compareAndSet(false, true)) {
            emit("A response is already being generated. Please wait.")
            return@flow
        }

        // Downscale image to 384x384 (strict requirement for Moondream 2)
        val downscaledBitmap = try {
            downscaleImage(imageUri, TARGET_IMAGE_SIZE, TARGET_IMAGE_SIZE)
        } catch (e: Exception) {
            emit("Failed to process image: ${e.message}")
            generationInProgress.set(false)
            return@flow
        }

        if (downscaledBitmap == null) {
            emit("Invalid or unreadable image. Please try again.")
            generationInProgress.set(false)
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
                    llamaAndroid().launchCompletionWithImage(
                        contextId = id,
                        bitmap = downscaledBitmap,
                        prompt = prompt.trim(),
                        params = completionParams(prompt)
                    )
                }
            } finally {
                tokenJob.cancel()
            }

            val error = result?.let { extractError(it) }
            if (!error.isNullOrBlank()) {
                emit("Vision inference failed: $error")
                return@flow
            }

            if (generatedText.isEmpty()) {
                val finalText = result?.let { extractFinalText(it) }
                if (!finalText.isNullOrBlank()) {
                    emit(finalText)
                } else {
                    emit("No response generated. The image may be unclear or unsupported.")
                }
            }
        } catch (e: CancellationException) {
            llamaHelper.stopPrediction()
            throw e
        } catch (e: Exception) {
            llamaHelper.stopPrediction()
            emit("Vision inference failed: ${e.message ?: "unknown error"}")
            Log.w(TAG, "Vision generation failed", e)
        } finally {
            downscaledBitmap.recycle()
            generationInProgress.set(false)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Downscale image to target dimensions (384x384) for Moondream 2.
     * This is critical for memory efficiency and model compatibility.
     */
    private fun downscaleImage(uri: Uri, targetWidth: Int, targetHeight: Int): Bitmap? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
                ?: return null

            // First decode to get dimensions
            val options = BitmapFactory.Options().apply {
                inJustDecodeBounds = true
            }
            
            // Read bounds without loading full bitmap
            val originalBitmap = BitmapFactory.decodeStream(inputStream, null, options)
            inputStream.close()

            // Calculate sample size for efficient downscaling
            val (width, height) = Pair(options.outWidth, options.outHeight)
            val sampleSize = calculateSampleSize(width, height, targetWidth, targetHeight)

            // Decode with sampling
            val newInputStream = contentResolver.openInputStream(uri)
                ?: return null
            
            val sampledOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }
            
            val sampledBitmap = BitmapFactory.decodeStream(newInputStream, null, sampledOptions)
            newInputStream.close()

            // Final resize to exact target dimensions
            val scaledBitmap = Bitmap.createScaledBitmap(
                sampledBitmap,
                targetWidth,
                targetHeight,
                true
            )
            
            if (sampledBitmap != scaledBitmap) {
                sampledBitmap.recycle()
            }

            scaledBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Image downscaling failed", e)
            null
        }
    }

    /**
     * Calculate optimal sample size for Bitmap decoding.
     */
    private fun calculateSampleSize(width: Int, height: Int, targetWidth: Int, targetHeight: Int): Int {
        var sampleSize = 1
        while (width / sampleSize > targetWidth * 2 || height / sampleSize > targetHeight * 2) {
            sampleSize *= 2
        }
        return sampleSize
    }

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
    
    private fun mmprojUri(): String = Uri.fromFile(mmprojFile).toString()

    private fun missingModelMessage(): String =
        "Vision model not found at ${modelFile.absolutePath}. Add moondream2.gguf to /sdcard/Hayat/"

    private fun missingMmprojMessage(): String =
        "MMProj file not found at ${mmprojFile.absolutePath}. Add moondream2-mmproj.gguf to /sdcard/Hayat/"

    override fun close() {
        llamaHelper.abort()
        llamaHelper.release()
        scope.cancel()
    }

    companion object {
        private const val TAG = "VisionInferenceService"
        private const val CONTEXT_LENGTH = 2048
        private const val MAX_TOKENS = 256
        private const val TEMPERATURE = 0.7
        private const val TOP_P = 0.9
        private const val TOP_K = 40
        private const val LOAD_TIMEOUT_MS = 120_000L
        
        // Strict 384x384 downscaling for Moondream 2 compatibility
        const val TARGET_IMAGE_SIZE = 384
    }
}
