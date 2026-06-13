package com.hayat.data

import kotlin.math.sqrt

class VectorSearchEngine(private val dao: MedicalChunkDao) {

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

    suspend fun findRelevantChunks(queryEmbedding: FloatArray): List<MedicalChunk> {
        return search(queryEmbedding).map { it.chunk }
    }

    data class SearchResult(
        val chunk: MedicalChunk,
        val similarity: Float
    )

    fun isResultValid(score: Float): Boolean {
        return score > 0.82f
    }

    fun getRefusalMessage(): String {
        return "I couldn't find relevant medical guidelines. Please consult a healthcare professional."
    }

    private fun calculateCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size) return 0f

        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0

        for (i in vectorA.indices) {
            dotProduct += vectorA[i].toDouble() * vectorB[i].toDouble()
            normA += vectorA[i].toDouble() * vectorA[i].toDouble()
            normB += vectorB[i].toDouble() * vectorB[i].toDouble()
        }

        val denominator = sqrt(normA) * sqrt(normB)
        return if (denominator > 0) (dotProduct / denominator).toFloat() else 0f
    }
}
