package com.hayat.services

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.LongBuffer
import kotlin.math.sqrt

class EmbeddingService(
    private val modelFile: File = File(DEFAULT_MODEL_PATH),
    private val tokenizer: WordPieceTokenizer = WordPieceTokenizer(),
) : AutoCloseable {
    constructor(context: Context) : this(
        modelFile = File(DEFAULT_MODEL_PATH),
        tokenizer = WordPieceTokenizer(context = context),
    )

    constructor(context: Context, modelFile: File) : this(
        modelFile = modelFile,
        tokenizer = WordPieceTokenizer(context = context),
    )

    private val TAG = "EmbeddingService"
    private val ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null

    init {
        loadModel()
    }

    suspend fun embed(text: String): List<Float> = withContext(Dispatchers.IO) {
        runCatching {
            embedUnsafe(text)
        }.getOrElse { exception ->
            Log.e(TAG, "Embedding generation failed: ${exception.message}")
            emptyList()
        }
    }

    suspend fun generateEmbedding(input: String): FloatArray? = withContext(Dispatchers.IO) {
        runCatching {
            embedUnsafe(input).toFloatArray()
        }.getOrNull()
    }

    fun normalizeText(input: String): String {
        return input
            .lowercase()
            .replace(NON_ALPHANUMERIC_REGEX, " ")
            .trim()
    }

    private fun loadModel() {
        if (ortSession != null) {
            return
        }

        if (!modelFile.isFile) {
            Log.e(TAG, "ONNX model not found at ${modelFile.absolutePath}")
            return
        }

        val options = OrtSession.SessionOptions()
        try {
            options.setIntraOpNumThreads(2)
            ortSession = ortEnv.createSession(modelFile.absolutePath, options)
            Log.d(TAG, "ONNX Model loaded successfully from ${modelFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ONNX model: ${e.message}")
        } finally {
            options.close()
        }
    }

    private fun embedUnsafe(text: String): List<Float> {
        val session = ortSession ?: run {
            Log.e(TAG, "Inference failed: ONNX Session is null")
            return emptyList()
        }

        val inputIds = tokenizer.tokenize(text)
        val shape = longArrayOf(1, inputIds.size.toLong())
        val inputTensor = OnnxTensor.createTensor(
            ortEnv,
            LongBuffer.wrap(LongArray(inputIds.size) { index -> inputIds[index].toLong() }),
            shape,
        )
        val attentionMask = LongArray(inputIds.size) { index ->
            if (inputIds[index] == 0) 0L else 1L
        }
        val attentionMaskTensor = OnnxTensor.createTensor(
            ortEnv,
            LongBuffer.wrap(attentionMask),
            shape,
        )

        try {
            val inputs = mapOf(
                "input_ids" to inputTensor,
                "attention_mask" to attentionMaskTensor,
            )

            session.run(inputs).use { results ->
                if (results.size() == 0) {
                    throw IllegalStateException("ONNX model returned no outputs")
                }

                val embedding = extractClsEmbedding(results.get(0).value)
                if (embedding.size != EMBEDDING_DIMENSION) {
                    throw IllegalStateException(
                        "Expected $EMBEDDING_DIMENSION-dimensional embedding but got ${embedding.size}",
                    )
                }

                return normalizeL2(embedding).toList()
            }
        } finally {
            inputTensor.close()
            attentionMaskTensor.close()
        }
    }

    private fun extractClsEmbedding(output: Any): FloatArray {
        val firstTokenEmbedding = when (output) {
            is FloatArray -> output
            is Array<*> -> {
                val firstBatch = output.firstOrNull()
                    ?: throw IllegalStateException("ONNX output batch is empty")

                when (firstBatch) {
                    is FloatArray -> firstBatch
                    is Array<*> -> {
                        val firstToken = firstBatch.firstOrNull()
                            ?: throw IllegalStateException("ONNX output token sequence is empty")

                        when (firstToken) {
                            is FloatArray -> firstToken
                            else -> throw IllegalStateException("Unexpected ONNX output token type: ${firstToken::class.java.name}")
                        }
                    }
                    else -> throw IllegalStateException("Unexpected ONNX output batch type: ${firstBatch::class.java.name}")
                }
            }
            else -> throw IllegalStateException("Unexpected ONNX output type: ${output::class.java.name}")
        }

        return firstTokenEmbedding.copyOf()
    }

    private fun normalizeL2(vector: FloatArray): FloatArray {
        val magnitude = sqrt(
            vector.sumOf { value ->
                value.toDouble() * value.toDouble()
            },
        ).toFloat()

        return if (magnitude > 1e-9f) {
            FloatArray(vector.size) { index -> vector[index] / magnitude }
        } else {
            FloatArray(vector.size)
        }
    }

    override fun close() {
        try {
            ortSession?.close()
            ortEnv.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ONNX resources: ${e.message}")
        }
    }

    companion object {
        private const val DEFAULT_MODEL_PATH = "/sdcard/Hayat/e5-small.onnx"
        private const val EMBEDDING_DIMENSION = 384
        private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9\\s]")
    }
}
