package com.hayat.services

import android.content.Context
import android.os.Build
import android.os.StatFs
import java.io.File

/**
 * Environmental Stress Testing Utilities for Phase 4
 * 
 * Tests app behavior under various stress conditions:
 * - Low memory scenarios
 * - Thermal throttling detection
 * - Storage space constraints
 * - CPU load simulation
 */
class StressTestUtils {

    companion object {
        private const val TAG = "StressTestUtils"
        
        // Memory thresholds (in MB)
        const val LOW_MEMORY_THRESHOLD_MB = 512
        const val CRITICAL_MEMORY_THRESHOLD_MB = 256
        
        // Storage thresholds (in MB)
        const val LOW_STORAGE_THRESHOLD_MB = 100
        const val CRITICAL_STORAGE_THRESHOLD_MB = 50
        
        // Temperature thresholds (Celsius)
        const val NORMAL_TEMP_C = 40
        const val WARNING_TEMP_C = 50
        const val CRITICAL_TEMP_C = 60
    }

    /**
     * Check available RAM on device
     */
    fun getAvailableMemoryMB(context: Context): Long {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager
        val memInfo = android.app.ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.availMem / (1024 * 1024)
    }

    /**
     * Check total RAM on device
     */
    fun getTotalMemoryMB(): Long {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024)
    }

    /**
     * Check available storage space
     */
    fun getAvailableStorageMB(): Long {
        val stat = StatFs("/sdcard/Hayat/")
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        return availableBytes / (1024 * 1024)
    }

    /**
     * Detect if device is thermally throttled
     * Note: Requires API 28+ for accurate temperature reading
     */
    fun isThermallyThrottled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // On Android 9+, we can check thermal status
                // This is a simplified check - production would use ThermalStatusListener
                val temp = getCpuTemperature()
                temp > WARNING_TEMP_C
            } else {
                false // Cannot reliably detect on older devices
            }
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Attempt to read CPU temperature (device-specific, may not work on all devices)
     */
    private fun getCpuTemperature(): Int {
        return try {
            // Try common thermal zone paths
            val thermalPaths = listOf(
                "/sys/class/thermal/thermal_zone0/temp",
                "/sys/class/thermal/thermal_zone1/temp",
                "/sys/devices/virtual/thermal/thermal_zone0/temp"
            )

            for (path in thermalPaths) {
                val file = File(path)
                if (file.exists()) {
                    val tempStr = file.readText().trim()
                    // Temperature is usually in millidegrees Celsius
                    return tempStr.toIntOrNull()?.div(1000) ?: NORMAL_TEMP_C
                }
            }
            NORMAL_TEMP_C
        } catch (e: Exception) {
            NORMAL_TEMP_C
        }
    }

    /**
     * Check if device meets minimum requirements for running models
     */
    fun meetsMinimumRequirements(context: Context): DeviceRequirements {
        val availableMem = getAvailableMemoryMB(context)
        val availableStorage = getAvailableStorageMB()
        val isThrottled = isThermallyThrottled()

        return DeviceRequirements(
            hasEnoughMemory = availableMem >= LOW_MEMORY_THRESHOLD_MB,
            hasEnoughStorage = availableStorage >= LOW_STORAGE_THRESHOLD_MB,
            isOverheated = isThrottled,
            availableMemoryMB = availableMem,
            availableStorageMB = availableStorage,
            cpuTemperatureC = getCpuTemperature()
        )
    }

    /**
     * Simulate memory pressure by allocating dummy arrays
     * Use with caution - for testing only!
     */
    fun simulateMemoryPressure(targetFreeMB: Int): List<ByteArray> {
        val allocatedBuffers = mutableListOf<ByteArray>()
        val targetBytes = targetFreeMB * 1024 * 1024
        var allocatedBytes = 0L

        try {
            while (allocatedBytes < targetBytes) {
                val bufferSize = minOf(10 * 1024 * 1024, (targetBytes - allocatedBytes).toInt())
                val buffer = ByteArray(bufferSize)
                allocatedBuffers.add(buffer)
                allocatedBytes += bufferSize
            }
        } catch (e: OutOfMemoryError) {
            // Expected during stress testing
        }

        return allocatedBuffers
    }

    /**
     * Clear simulated memory pressure
     */
    fun clearMemoryPressure(buffers: List<ByteArray>) {
        buffers.clear()
        System.gc()
    }

    /**
     * Get device classification based on capabilities
     */
    fun getDeviceClass(context: Context): DeviceClass {
        val totalMem = getTotalMemoryMB()
        val requirements = meetsMinimumRequirements(context)

        return when {
            totalMem >= 4096 && requirements.hasEnoughMemory -> DeviceClass.HIGH_END
            totalMem >= 2048 && requirements.hasEnoughMemory -> DeviceClass.MID_RANGE
            else -> DeviceClass.LOW_END
        }
    }
}

/**
 * Device capability classification
 */
enum class DeviceClass {
    HIGH_END,      // 4GB+ RAM, suitable for larger models
    MID_RANGE,     // 2-4GB RAM, suitable for medium models
    LOW_END        // <2GB RAM, requires smallest models
}

/**
 * Device requirements check result
 */
data class DeviceRequirements(
    val hasEnoughMemory: Boolean,
    val hasEnoughStorage: Boolean,
    val isOverheated: Boolean,
    val availableMemoryMB: Long,
    val availableStorageMB: Long,
    val cpuTemperatureC: Int
) {
    fun isReadyForInference(): Boolean {
        return hasEnoughMemory && hasEnoughStorage && !isOverheated
    }

    fun getWarnings(): List<String> {
        val warnings = mutableListOf<String>()
        if (!hasEnoughMemory) warnings.add("Low memory: ${availableMemoryMB}MB available")
        if (!hasEnoughStorage) warnings.add("Low storage: ${availableStorageMB}MB available")
        if (isOverheated) warnings.add("Device overheating: ${cpuTemperatureC}°C")
        return warnings
    }
}
