package com.hayat.services

import android.content.Context
import android.util.Log
import ai.onnxruntime.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.LongBuffer
import kotlin.math.sqrt

class EmbeddingService(
    private val context: Context,
    private val modelFile: File = File(context.getExternalFilesDir(null), "e5-small.onnx")
) : AutoCloseable {
    private val TAG = "EmbeddingService"

    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    private var tokenizer: WordPieceTokenizer? = null

    init {
        setupONNX()
        setupTokenizer()
    }

    private fun setupONNX() {
        try {
            ortEnv = OrtEnvironment.getEnvironment()
            if (modelFile.exists()) {
                val options = OrtSession.SessionOptions()
                options.setIntraOpNumThreads(2)
                ortSession = ortEnv?.createSession(modelFile.absolutePath, options)
                Log.d(TAG, "ONNX Model loaded successfully from ${modelFile.absolutePath}")
            } else {
                Log.e(TAG, "Model file not found at ${modelFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ONNX model: ${e.message}")
        }
    }

    private fun setupTokenizer() {
        try {
            tokenizer = WordPieceTokenizer(context)
            Log.d(TAG, "Tokenizer initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing tokenizer: ${e.message}")
        }
    }

    suspend fun embed(text: String): FloatArray? = withContext(Dispatchers.Default) {
        withTimeoutOrNull(5000L) {
            var inputTensor: OnnxTensor? = null
            var attentionMaskTensor: OnnxTensor? = null
            try {
                val session = ortSession ?: run {
                    Log.e(TAG, "Inference failed: ONNX Session is null. Model file must exist at ${modelFile.absolutePath}")
                    return@withTimeoutOrNull null
                }

                val tokenizerInstance = tokenizer ?: run {
                    Log.e(TAG, "Inference failed: Tokenizer not initialized")
                    return@withTimeoutOrNull null
                }

                val processedText = "query: " + normalizeText(text)
                val inputIds = tokenizerInstance.tokenize(processedText, maxLength = 128)

                val inputIdsLong = LongArray(inputIds.size) { i -> inputIds[i].toLong() }
                val shape = longArrayOf(1, inputIdsLong.size.toLong())

                inputTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(inputIdsLong), shape)
                val attentionMask = LongArray(inputIdsLong.size) { 1L }
                attentionMaskTensor = OnnxTensor.createTensor(ortEnv, LongBuffer.wrap(attentionMask), shape)

                val inputs = mapOf(
                    "input_ids" to inputTensor,
                    "attention_mask" to attentionMaskTensor
                )

                session.run(inputs).use { results ->
                    if (results.size() == 0) {
                        Log.e(TAG, "No output from model")
                        return@use null
                    }

                    val outputTensor = results[0].value

                    val rawOutput = when (outputTensor) {
                        is Array<*> -> extractFloatArray(outputTensor)
                        is FloatArray -> outputTensor
                        else -> null
                    }

                    rawOutput?.let { extractClsEmbedding(it) }?.let { normalizeL2(it) }
                }
            } catch (e: OrtException) {
                Log.e(TAG, "ONNX inference error: ${e.message}")
                null
            } catch (e: Exception) {
                Log.e(TAG, "Inference error: ${e.message}", e)
                null
            } finally {
                try {
                    inputTensor?.close()
                    attentionMaskTensor?.close()
                } catch (e: Exception) {
                    Log.w(TAG, "Error closing tensors: ${e.message}")
                }
            }
        } ?: run {
            Log.e(TAG, "Embedding generation timed out.")
            null
        }
    }

    private fun extractFloatArray(outputTensor: Array<*>): FloatArray? {
        return try {
            if (outputTensor.isNotEmpty() && outputTensor[0] is FloatArray) {
                outputTensor[0] as FloatArray
            } else if (outputTensor.isNotEmpty() && outputTensor[0] is Array<*>) {
                extractFloatArray(outputTensor[0] as Array<*>)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting float array: ${e.message}")
            null
        }
    }

    private fun extractClsEmbedding(embeddings: FloatArray): FloatArray? {
        return try {
            val embeddingDim = 384
            if (embeddings.size < embeddingDim) {
                Log.e(TAG, "Wrong embedding shape: expected at least $embeddingDim, got ${embeddings.size}")
                return null
            }
            
            embeddings.take(embeddingDim).toFloatArray()
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting CLS embedding: ${e.message}")
            null
        }
    }

    private fun normalizeL2(vector: FloatArray): FloatArray {
        var sumSquared = 0.0
        for (value in vector) {
            sumSquared += value.toDouble() * value
        }
        val magnitude = sqrt(sumSquared).toFloat()

        return if (magnitude > 1e-9f) {
            FloatArray(vector.size) { i -> vector[i] / magnitude }
        } else {
            vector
        }
    }

    private fun normalizeText(input: String): String {
        return input.lowercase().replace(Regex("[^a-z0-9\\s]"), "").trim()
    }

    override fun close() {
        try {
            ortSession?.close()
            ortEnv?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ONNX resources: ${e.message}")
        }
    }
}