package com.hayat.data

import kotlin.math.sqrt

class VectorSearchEngine(private val dao: MedicalChunkDao) {

    private val TOP_K_LIMIT = 5

    suspend fun findRelevantChunks(queryEmbedding: FloatArray?): List<MedicalChunk> {
        if (queryEmbedding == null) return emptyList()
        
        val allChunks = dao.getAllChunks()
        return allChunks
            .map { chunk ->
                val score = calculateCosineSimilarity(queryEmbedding, chunk.embedding)
                chunk to score
            }
            .filter { (_, score) ->
                isResultValid(score)
            }
            .sortedByDescending { (_, score) ->
                score
            }
            .take(TOP_K_LIMIT)
            .map { (chunk, _) ->
                chunk
            }
    }

    fun isResultValid(score: Float): Boolean {
        return score > 0.82f
    }

    fun getRefusalMessage(): String {
        return "نأسف، لكننا لم نتمكن من العثور على بروتوكولات طبية ذات صلة للإجابة على طلبك بثقة."
    }

    private fun calculateCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray?): Float {
        if (vectorB == null) return 0f
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
