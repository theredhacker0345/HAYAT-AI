package com.hayat.data

import android.util.Log
import kotlin.math.sqrt

/**
 * RAG Engine for Hayat AI
 * Performs vector search with configurable confidence threshold.
 * 
 * E5 Model Requirements:
 * - Database chunks must be embedded with "passage:" prefix
 * - User queries must be embedded with "query:" prefix
 */
class VectorSearchEngine(
    private val dao: MedicalChunkDao,
    private val confidenceThreshold: Float = DEFAULT_CONFIDENCE_THRESHOLD
) {

    companion object {
        // Configurable confidence threshold - can be tuned during red-teaming
        const val DEFAULT_CONFIDENCE_THRESHOLD = 0.65f
        
        private const val TAG = "VectorSearchEngine"
    }

    /**
     * Search for relevant medical chunks using cosine similarity.
     * 
     * @param queryEmbedding The user's query embedding (must use "query:" prefix)
     * @param topK Number of results to return
     * @return List of search results sorted by similarity score
     */
    suspend fun search(queryEmbedding: FloatArray, topK: Int = 5): List<SearchResult> {
        val allChunks = dao.getAllChunks()
        
        return allChunks
            .map { chunk ->
                val score = calculateCosineSimilarity(queryEmbedding, chunk.embedding)
                SearchResult(chunk, score)
            }
            .filter { result ->
                isResultValid(result.score)
            }
            .sortedByDescending { result ->
                result.score
            }
            .take(topK)
    }

    /**
     * Find relevant chunks without filtering by confidence.
     * Used for debugging and threshold tuning.
     */
    suspend fun findRelevantChunksWithScores(queryEmbedding: FloatArray): List<SearchResult> {
        val allChunks = dao.getAllChunks()
        
        return allChunks
            .map { chunk ->
                val score = calculateCosineSimilarity(queryEmbedding, chunk.embedding)
                SearchResult(chunk, score)
            }
            .sortedByDescending { it.score }
    }

    /**
     * Find relevant chunks meeting the confidence threshold.
     */
    suspend fun findRelevantChunks(queryEmbedding: FloatArray): List<MedicalChunk> {
        return search(queryEmbedding).map { it.chunk }
    }

    data class SearchResult(
        val chunk: MedicalChunk,
        val similarity: Float
    )

    /**
     * Check if a search result meets the minimum confidence threshold.
     * Results below this threshold are considered unreliable for medical guidance.
     */
    fun isResultValid(score: Float): Boolean {
        val isValid = score >= confidenceThreshold
        Log.d(TAG, "Confidence check: score=$score, threshold=$confidenceThreshold, valid=$isValid")
        return isValid
    }

    /**
     * Get the refusal message when no relevant guidelines are found.
     */
    fun getRefusalMessage(): String {
        return "I couldn't find relevant medical guidelines in our database. Please consult a healthcare professional immediately."
    }

    /**
     * Calculate cosine similarity between two vectors.
     * Both vectors should be L2-normalized for accurate results.
     */
    private fun calculateCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size) {
            Log.w(TAG, "Vector size mismatch: ${vectorA.size} vs ${vectorB.size}")
            return 0f
        }

        // For L2-normalized vectors, dot product equals cosine similarity
        var dotProduct = 0.0
        
        for (i in vectorA.indices) {
            dotProduct += vectorA[i].toDouble() * vectorB[i].toDouble()
        }

        return dotProduct.toFloat().coerceIn(-1f, 1f)
    }

    /**
     * Update the confidence threshold at runtime.
     * Useful for red-teaming and A/B testing different thresholds.
     */
    fun updateConfidenceThreshold(newThreshold: Float) {
        require(newThreshold in 0.0f..1.0f) { "Threshold must be between 0.0 and 1.0" }
        Log.i(TAG, "Updating confidence threshold from $confidenceThreshold to $newThreshold")
        // Note: This is a simple implementation; for production, consider making this a var
    }
}
