package com.hayat.services

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Model Manager for efficient memory management between text and vision models.
 * Implements model swapping to prevent RAM exhaustion on low-end devices.
 * 
 * Key Features:
 * - Unloads text model when using vision, reloads after
 * - Prevents both models from being loaded simultaneously
 * - Thread-safe operations with mutex locks
 * - Automatic cleanup on app lifecycle events
 */
class ModelManager(
    private val textInferenceService: ModelInferenceService,
    private val visionInferenceService: VisionInferenceService
) : AutoCloseable {

    private val managerMutex = Mutex()
    private var activeModelType: ModelType? = null
    
    enum class ModelType { TEXT, VISION }

    /**
     * Get the currently active model type, if any.
     */
    fun getActiveModel(): ModelType? = activeModelType

    /**
     * Check if a specific model type is currently loaded.
     */
    fun isModelLoaded(type: ModelType): Boolean = activeModelType == type

    /**
     * Load text model, unloading vision model if necessary.
     */
    suspend fun loadTextModel(): Boolean = managerMutex.withLock {
        try {
            // Unload vision model if it's active
            if (activeModelType == ModelType.VISION) {
                Log.d(TAG, "Unloading vision model to load text model")
                unloadVisionModel()
            }

            // Load text model if not already loaded
            if (activeModelType != ModelType.TEXT) {
                val success = textInferenceService.loadModel()
                if (success) {
                    activeModelType = ModelType.TEXT
                    Log.d(TAG, "Text model loaded successfully")
                } else {
                    Log.e(TAG, "Failed to load text model: ${textInferenceService.lastError}")
                    return@withLock false
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading text model", e)
            false
        }
    }

    /**
     * Load vision model, unloading text model if necessary.
     */
    suspend fun loadVisionModel(): Boolean = managerMutex.withLock {
        try {
            // Unload text model if it's active
            if (activeModelType == ModelType.TEXT) {
                Log.d(TAG, "Unloading text model to load vision model")
                unloadTextModel()
            }

            // Load vision model if not already loaded
            if (activeModelType != ModelType.VISION) {
                val success = visionInferenceService.loadModel()
                if (success) {
                    activeModelType = ModelType.VISION
                    Log.d(TAG, "Vision model loaded successfully")
                } else {
                    Log.e(TAG, "Failed to load vision model: ${visionInferenceService.lastError}")
                    return@withLock false
                }
            }

            true
        } catch (e: Exception) {
            Log.e(TAG, "Error loading vision model", e)
            false
        }
    }

    /**
     * Unload text model explicitly.
     */
    fun unloadTextModel() {
        if (activeModelType == ModelType.TEXT) {
            textInferenceService.close()
            activeModelType = null
            Log.d(TAG, "Text model unloaded")
        }
    }

    /**
     * Unload vision model explicitly.
     */
    fun unloadVisionModel() {
        if (activeModelType == ModelType.VISION) {
            visionInferenceService.close()
            activeModelType = null
            Log.d(TAG, "Vision model unloaded")
        }
    }

    /**
     * Unload all models to free memory.
     */
    fun unloadAllModels() {
        unloadTextModel()
        unloadVisionModel()
        Log.d(TAG, "All models unloaded")
    }

    /**
     * Switch from current model to another model type.
     * Useful for transitioning between text chat and image analysis.
     */
    suspend fun switchTo(targetType: ModelType): Boolean {
        return when (targetType) {
            ModelType.TEXT -> loadTextModel()
            ModelType.VISION -> loadVisionModel()
        }
    }

    /**
     * Get memory usage estimate (in MB) for the active model.
     * These are approximate values based on Q4_K_M quantization.
     */
    fun getEstimatedMemoryUsage(): Int {
        return when (activeModelType) {
            ModelType.TEXT -> 512  // Llama-3.2-1B ~512MB
            ModelType.VISION -> 768 // Moondream-2 ~768MB (includes mmproj)
            null -> 0
        }
    }

    /**
     * Check if switching to a target model is safe given available memory.
     * This is a placeholder - actual implementation would query system memory.
     */
    fun canSwitchTo(targetType: ModelType): Boolean {
        // Conservative estimate: require at least 1GB free RAM for safe operation
        val requiredMemory = when (targetType) {
            ModelType.TEXT -> 512
            ModelType.VISION -> 768
        }
        
        // In production, this would check actual available memory
        // For now, assume it's safe if no model is loaded or switching within same type
        return activeModelType == targetType || activeModelType == null
    }

    override fun close() {
        unloadAllModels()
        Log.d(TAG, "ModelManager closed")
    }

    companion object {
        private const val TAG = "ModelManager"
    }
}
