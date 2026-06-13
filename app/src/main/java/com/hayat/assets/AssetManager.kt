package com.hayat.assets

import android.content.Context
import android.content.res.AssetManager as AndroidAssetManager
import android.util.Log
import java.io.File
import java.io.IOException

object AssetManager {
    const val HAYAT_FOLDER_PATH = "/sdcard/Hayat/"

    private const val TAG = "AssetManager"
    private val REQUIRED_FILES = listOf("medical.db", "e5-small.onnx", "model.gguf")

    enum class State {
        PRESENT,
        MISSING,
        SIZE_MISMATCH
    }

    data class AssetStatus(
        val fileName: String,
        val expectedSizeBytes: Long?,
        val presentSizeBytes: Long?,
        val state: State
    ) {
        val isPresent: Boolean = state == State.PRESENT
        val isMissing: Boolean = state != State.PRESENT
    }

    fun hayatFolder(): File = File(HAYAT_FOLDER_PATH)

    fun checkAssets(
        context: Context,
        assetManager: AndroidAssetManager = context.assets
    ): List<AssetStatus> {
        val folder = hayatFolder()
        return REQUIRED_FILES.map { fileName ->
            val file = File(folder, fileName)
            val presentSize = file.takeIf { it.isFile }?.length()
            val expectedSize = bundledAssetSize(assetManager, fileName)
            val state = when {
                !file.isFile -> State.MISSING
                expectedSize != null && expectedSize > 0L && presentSize != expectedSize -> State.SIZE_MISMATCH
                else -> State.PRESENT
            }

            AssetStatus(
                fileName = fileName,
                expectedSizeBytes = expectedSize,
                presentSizeBytes = presentSize,
                state = state
            )
        }
    }

    fun copyBundledAssets(
        context: Context,
        assetManager: AndroidAssetManager = context.assets
    ): List<AssetStatus> {
        val folder = hayatFolder()
        if (!folder.exists() && !folder.mkdirs()) {
            Log.w(TAG, "Unable to create asset folder: ${folder.absolutePath}")
            return checkAssets(context, assetManager)
        }

        REQUIRED_FILES.forEach { fileName ->
            val destination = File(folder, fileName)
            val expectedSize = bundledAssetSize(assetManager, fileName)
            val canSkip = destination.isFile && destination.length() > 0L &&
                (expectedSize == null || destination.length() == expectedSize)
            if (canSkip) return@forEach

            val sourcePath = bundledAssetPath(assetManager, fileName) ?: return@forEach
            try {
                assetManager.open(sourcePath).use { input ->
                    destination.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied bundled asset $fileName to ${destination.absolutePath}")
            } catch (e: IOException) {
                Log.w(TAG, "Unable to copy bundled asset $fileName: ${e.message}")
            } catch (e: SecurityException) {
                Log.w(TAG, "Unable to write asset folder: ${e.message}")
            }
        }

        return checkAssets(context, assetManager)
    }

    fun hasAllAssets(
        context: Context,
        assetManager: AndroidAssetManager = context.assets
    ): Boolean = checkAssets(context, assetManager).all { it.state == State.PRESENT }

    fun hasBundledAssets(
        context: Context,
        assetManager: AndroidAssetManager = context.assets
    ): Boolean = REQUIRED_FILES.any { bundledAssetPath(assetManager, it) != null }

    private fun bundledAssetSize(
        assetManager: AndroidAssetManager,
        fileName: String
    ): Long? {
        val path = bundledAssetPath(assetManager, fileName) ?: return null
        return try {
            assetManager.openFd(path).length
        } catch (e: IOException) {
            try {
                assetManager.open(path).use { it.readBytes().size.toLong() }
            } catch (inner: IOException) {
                null
            }
        } catch (e: UnsupportedOperationException) {
            null
        }
    }

    private fun bundledAssetPath(
        assetManager: AndroidAssetManager,
        fileName: String
    ): String? {
        val topLevelEntries = runCatching { assetManager.list("") ?: emptyArray() }
            .getOrDefault(emptyArray())

        if (fileName in topLevelEntries) return fileName

        val matches = mutableListOf<String>()
        fun search(directory: String) {
            val entries = runCatching { assetManager.list(directory) ?: emptyArray() }
                .getOrDefault(emptyArray())

            entries.forEach { entry ->
                val path = if (directory.isEmpty()) entry else "$directory/$entry"
                val children = runCatching { assetManager.list(path) ?: emptyArray() }
                    .getOrDefault(emptyArray())

                if (children.isEmpty()) {
                    if (entry == fileName) matches += path
                } else {
                    search(path)
                }
            }
        }

        topLevelEntries.forEach { search(it) }
        return matches.firstOrNull()
    }
}
