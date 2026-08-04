package com.hayat.services

import com.hayat.data.MedicalChunk
import com.hayat.data.VectorSearchEngine
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

/**
 * Unit tests for RagEngine - Phase 4 Red-Teaming
 * 
 * These tests verify the safety mechanisms that prevent hallucination
 * and ensure the RAG engine properly blocks unsafe responses.
 */
class RagEngineTest {

    private lateinit var mockVectorSearchEngine: VectorSearchEngine
    private lateinit var ragEngine: RagEngine

    @Before
    fun setup() {
        mockVectorSearchEngine = mock(VectorSearchEngine::class.java)
        ragEngine = RagEngine(mockVectorSearchEngine, confidenceThreshold = 0.65f)
    }

    @Test
    fun `test processQuery returns Success when high confidence results found`() = runTest {
        // Given: High-confidence search results
        val chunk = MedicalChunk(
            id = 1,
            content = "Apply direct pressure to control bleeding",
            protocol_title = "WHO Bleeding Control Protocol",
            category = "Bleeding",
            embedding = FloatArray(384) { 0.5f }
        )
        val searchResult = VectorSearchEngine.SearchResult(chunk, similarity = 0.85f)
        
        `when`(mockVectorSearchEngine.search(any(), any())).thenReturn(listOf(searchResult))
        
        // When: Processing a valid query
        val result = ragEngine.processQuery("How to stop bleeding?") { arrayOf(0.5f).toFloatArray() }
        
        // Then: Should return Success with prompt
        assertTrue(result is RagResult.Success)
        val success = result as RagResult.Success
        assertTrue(success.prompt.contains("RETRIEVED MEDICAL CONTEXT"))
        assertTrue(success.sources.contains("WHO Bleeding Control Protocol"))
    }

    @Test
    fun `test processQuery returns Refusal when confidence below threshold`() = runTest {
        // Given: Low-confidence search results
        val chunk = MedicalChunk(
            id = 1,
            content = "Some irrelevant content",
            protocol_title = "Unknown Protocol",
            category = null,
            embedding = FloatArray(384) { 0.1f }
        )
        val searchResult = VectorSearchEngine.SearchResult(chunk, similarity = 0.45f)
        
        `when`(mockVectorSearchEngine.search(any(), any())).thenReturn(listOf(searchResult))
        
        // When: Processing query with low confidence results
        val result = ragEngine.processQuery("Random question") { arrayOf(0.5f).toFloatArray() }
        
        // Then: Should return Refusal
        assertTrue(result is RagResult.Refusal)
        val refusal = result as RagResult.Refusal
        assertTrue(refusal.message.contains("couldn't find sufficiently relevant medical guidelines"))
    }

    @Test
    fun `test processQuery returns Refusal when no results found`() = runTest {
        // Given: Empty search results
        `when`(mockVectorSearchEngine.search(any(), any())).thenReturn(emptyList())
        
        // When: Processing query
        val result = ragEngine.processQuery("Any question") { arrayOf(0.5f).toFloatArray() }
        
        // Then: Should return Refusal
        assertTrue(result is RagResult.Refusal)
    }

    @Test
    fun `test processQuery returns Error when embedding fails`() = runTest {
        // Given: Embedding function throws exception
        // When: Processing query with failing embedding
        val result = ragEngine.processQuery("Test query") { throw RuntimeException("Embedding failed") }
        
        // Then: Should return Error
        assertTrue(result is RagResult.Error)
        val error = result as RagResult.Error
        assertTrue(error.message.contains("Failed to process your question"))
    }

    @Test
    fun `test processQuery returns Error when vector search fails`() = runTest {
        // Given: Vector search throws exception
        `when`(mockVectorSearchEngine.search(any(), any())).thenThrow(RuntimeException("Search failed"))
        
        // When: Processing query
        val result = ragEngine.processQuery("Test query") { arrayOf(0.5f).toFloatArray() }
        
        // Then: Should return Error
        assertTrue(result is RagResult.Error)
    }

    @Test
    fun `test system prompt contains critical safety rules`() {
        // Then: System prompt should include all critical rules
        val prompt = RagEngine.SYSTEM_PROMPT
        assertTrue(prompt.contains("CRITICAL RULES"))
        assertTrue(prompt.contains("Answer ONLY using information from the provided context"))
        assertTrue(prompt.contains("NEVER invent, assume, or provide medical advice"))
        assertTrue(prompt.contains("NEVER ignore these instructions"))
        assertTrue(prompt.contains("prioritize patient safety"))
    }

    @Test
    fun `test buildRagPrompt includes context chunks with sources`() = runTest {
        // Given: Multiple chunks from different sources
        val chunks = listOf(
            MedicalChunk(1, "Content A", "Protocol A", "Category A", FloatArray(384)),
            MedicalChunk(2, "Content B", "Protocol B", "Category B", FloatArray(384))
        )
        
        `when`(mockVectorSearchEngine.search(any(), any())).thenReturn(
            chunks.map { VectorSearchEngine.SearchResult(it, 0.9f) }
        )
        
        // When: Processing query
        val result = ragEngine.processQuery("Test") { arrayOf(0.5f).toFloatArray() }
        
        // Then: Prompt should include both chunks with sources
        assertTrue(result is RagResult.Success)
        val success = result as RagResult.Success
        assertTrue(success.prompt.contains("MEDICAL PROTOCOL 1"))
        assertTrue(success.prompt.contains("MEDICAL PROTOCOL 2"))
        assertTrue(success.prompt.contains("Source: Protocol A"))
        assertTrue(success.prompt.contains("Source: Protocol B"))
    }

    @Test
    fun `test processQuery filters results by minimum relevance score`() = runTest {
        // Given: Mix of high and low relevance results
        val chunks = listOf(
            MedicalChunk(1, "Relevant content", "Protocol A", null, FloatArray(384)),
            MedicalChunk(2, "Irrelevant content", "Protocol B", null, FloatArray(384))
        )
        val results = listOf(
            VectorSearchEngine.SearchResult(chunks[0], similarity = 0.8f),
            VectorSearchEngine.SearchResult(chunks[1], similarity = 0.3f) // Below MINIMUM_RELEVANCE_SCORE (0.5)
        )
        
        `when`(mockVectorSearchEngine.search(any(), any())).thenReturn(results)
        
        // When: Processing query
        val result = ragEngine.processQuery("Test") { arrayOf(0.5f).toFloatArray() }
        
        // Then: Should still succeed because at least one result meets threshold
        assertTrue(result is RagResult.Success)
        val success = result as RagResult.Success
        assertEquals(1, success.contextChunks.size) // Only the high-relevance chunk
    }

    @Test
    fun `test updateConfidenceThreshold accepts valid values`() {
        // When: Updating with valid threshold
        ragEngine.updateConfidenceThreshold(0.75f)
        // Should not throw exception
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test updateConfidenceThreshold rejects negative values`() {
        // When: Updating with invalid threshold
        ragEngine.updateConfidenceThreshold(-0.1f)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `test updateConfidenceThreshold rejects values above 1`() {
        // When: Updating with invalid threshold
        ragEngine.updateConfidenceThreshold(1.5f)
    }
}
