package com.hayat.services

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.math.sqrt

class EmbeddingService(
    private val context: Context,
    private val modelFile: File
) : AutoCloseable {

    private val TAG = "EmbeddingService"

    private val MAX_SEQ_LENGTH = 128
    private val EMBEDDING_DIM = 384

    private var ortEnv: OrtEnvironment = OrtEnvironment.getEnvironment()
    private var ortSession: OrtSession? = null
    private val vocab: Map<String, Int> by lazy { loadVocab() }

    init {
        loadModel()
    }

    private fun loadVocab(): Map<String, Int> {
        return try {
            val inputStream = context.assets.open("vocab.json")
            val reader = BufferedReader(InputStreamReader(inputStream))
            val json = JSONObject(reader.readText())
            reader.close()
            inputStream.close()

            val map = mutableMapOf<String, Int>()
            json.keys().forEach { key ->
                map[key] = json.getInt(key)
            }
            map
        } catch (e: Exception) {
            Log.w(TAG, "Could not load vocab.json from assets, using fallback. ${e.message}")
            emptyMap()
        }
    }

    private fun loadModel() {
        try {
            if (modelFile.exists()) {
                val options = OrtSession.SessionOptions()
                options.setIntraOpNumThreads(2)
                options.setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                ortSession = ortEnv.createSession(modelFile.absolutePath, options)
                Log.d(TAG, "ONNX Model loaded from ${modelFile.absolutePath}")
            } else {
                Log.e(TAG, "Model file not found: ${modelFile.absolutePath}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading ONNX model: ${e.message}")
        }
    }

    fun normalizeText(input: String): String {
        return input.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").trim()
    }

    private fun tokenize(text: String): Pair<LongArray, LongArray> {
        val e5Query = "query: $text"

        val wordPieces = mutableListOf<Int>()
        wordPieces.add(vocab["[CLS]"] ?: 101)

        e5Query.split(" ").forEach { word ->
            if (word.isEmpty()) return@forEach

            if (vocab.containsKey(word)) {
                wordPieces.add(vocab[word]!!)
            } else {
                wordPieces.addAll(wordPieceTokenize(word))
            }
        }

        wordPieces.add(vocab["[SEP]"] ?: 102)

        val inputIds = wordPieces.toLongArray()
        val attentionMask = LongArray(inputIds.size) { 1L }

        val paddedInputIds = inputIds.copyOf(MAX_SEQ_LENGTH)
        val paddedAttentionMask = attentionMask.copyOf(MAX_SEQ_LENGTH)

        for (i in inputIds.size until MAX_SEQ_LENGTH) {
            paddedInputIds[i] = vocab["[PAD]"]?.toLong() ?: 0L
            paddedAttentionMask[i] = 0L
        }

        return Pair(paddedInputIds, paddedAttentionMask)
    }

    private fun wordPieceTokenize(word: String): List<Int> {
        var remaining = word
        val subTokens = mutableListOf<Int>()
        var isBad = false

        while (remaining.isNotEmpty()) {
            var start = 0
            var end = remaining.length
            var matchedToken: String? = null

            while (start < end) {
                val substr = remaining.substring(start, end)
                val tokenId: Int? = when {
                    start > 0 && vocab.containsKey("##$substr") -> vocab["##$substr"]
                    start == 0 && vocab.containsKey(substr) -> vocab[substr]
                    else -> null
                }

                if (tokenId != null) {
                    matchedToken = tokenId.toString()
                    break
                }
                end -= 1
            }

            if (matchedToken == null) {
                isBad = true
                break
            }

            subTokens.add(matchedToken.toInt())
            remaining = remaining.substring(end)
        }

        if (isBad) {
            return listOf(vocab["[UNK]"] ?: 100)
        }

        return subTokens
    }

    suspend fun generateEmbedding(input: String): FloatArray? = withContext(Dispatchers.IO) {
        val session = ortSession ?: run {
            Log.e(TAG, "Inference failed: ONNX Session is null. Model not loaded.")
            return@withContext null
        }

        try {
            if (vocab.isEmpty() || vocab["[CLS]"] == null) {
                Log.e(TAG, "Tokenizer not initialized: vocab.json not loaded.")
                return@withContext null
            }

            val (inputIds, attentionMask) = tokenize(input)

            if (inputIds.isEmpty()) {
                Log.e(TAG, "Tokenization produced empty input.")
                return@withContext null
            }

            val inputShape = longArrayOf(1, MAX_SEQ_LENGTH.toLong())
            val tokenTypeShape = longArrayOf(1, MAX_SEQ_LENGTH.toLong())

            val inputTensor = OnnxTensor.createTensor(
                ortEnv,
                java.nio.LongBuffer.wrap(inputIds),
                inputShape
            )

            val attentionMaskTensor = OnnxTensor.createTensor(
                ortEnv,
                java.nio.LongBuffer.wrap(attentionMask),
                inputShape
            )

            val tokenTypeIds = LongArray(MAX_SEQ_LENGTH) { 0L }
            val tokenTypeTensor = OnnxTensor.createTensor(
                ortEnv,
                java.nio.LongBuffer.wrap(tokenTypeIds),
                tokenTypeShape
            )

            val inputs = mapOf(
                "input_ids" to inputTensor,
                "attention_mask" to attentionMaskTensor,
                "token_type_ids" to tokenTypeTensor
            )

            val result = session.run(inputs)

            try {
                if (result.size() == 0) {
                    Log.e(TAG, "Model returned empty output.")
                    return@withContext null
                }

                val outputTensor = result.get(0).value as? ai.onnxruntime.OrtValue
                    ?: run {
                        Log.e(TAG, "Unexpected output type: ${result.get(0).value?.javaClass}")
                        return@withContext null
                    }

                val floatBuffer = outputTensor.floatBuffer
                val embedding = FloatArray(EMBEDDING_DIM)
                for (i in 0 until EMBEDDING_DIM) {
                    embedding[i] = if (floatBuffer.hasRemaining()) floatBuffer.get() else 0f
                }

                if (embedding.any { it.isNaN() || it.isInfinite() }) {
                    Log.e(TAG, "Embedding contains NaN or Infinity.")
                    return@withContext null
                }

                return@withContext normalizeL2(embedding)
            } finally {
                result.close()
                inputTensor.close()
                attentionMaskTensor.close()
                tokenTypeTensor.close()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}", e)
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

    fun isModelLoaded(): Boolean = ortSession != null

    fun isTokenizerReady(): Boolean = vocab.isNotEmpty() && vocab.containsKey("[CLS]")

    override fun close() {
        try {
            ortSession?.close()
            ortEnv.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error closing ONNX resources: ${e.message}")
        }
    }
}
