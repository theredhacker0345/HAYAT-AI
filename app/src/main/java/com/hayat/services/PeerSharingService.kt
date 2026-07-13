package com.hayat.services

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * PeerSharingService handles P2P sharing of the Hayat AI APK and assets
 * via Bluetooth, WiFi Direct, and NFC.
 */
class PeerSharingService(private val context: Context) {
    
    private val TAG = "PeerSharingService"
    
    /**
     * Export and share the APK file
     */
    suspend fun shareApk(): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val apkFile = getApkFile()
            
            if (!apkFile.exists()) {
                Log.e(TAG, "APK file not found at ${apkFile.absolutePath}")
                return@withContext Result.failure(FileNotFoundException("APK file not found"))
            }
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            
            Log.d(TAG, "APK ready for sharing: ${uri.toString()}")
            Result.success(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing APK for sharing: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Create share intent for APK
     */
    fun createShareIntent(apkUri: Uri): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.android.package-archive"
            putExtra(Intent.EXTRA_STREAM, apkUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    
    /**
     * Share medical database
     */
    suspend fun shareDatabase(): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val dbFile = File("/sdcard/Hayat/medical.db")
            
            if (!dbFile.exists()) {
                Log.e(TAG, "Database file not found")
                return@withContext Result.failure(FileNotFoundException("Database not found"))
            }
            
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                dbFile
            )
            
            Log.d(TAG, "Database ready for sharing")
            Result.success(uri)
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing database for sharing: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Share all assets as a bundle
     */
    suspend fun shareAllAssets(): Result<List<Uri>> = withContext(Dispatchers.IO) {
        try {
            val uris = mutableListOf<Uri>()
            val hayatFolder = File("/sdcard/Hayat/")
            
            if (!hayatFolder.exists()) {
                Log.e(TAG, "Hayat folder not found")
                return@withContext Result.failure(FileNotFoundException("Hayat folder not found"))
            }
            
            // Add database
            File(hayatFolder, "medical.db").takeIf { it.exists() }?.let {
                uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it))
            }
            
            // Add model if exists
            File(hayatFolder, "model.gguf").takeIf { it.exists() }?.let {
                uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it))
            }
            
            // Add embedding model if exists
            File(hayatFolder, "e5-small.onnx").takeIf { it.exists() }?.let {
                uris.add(FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", it))
            }
            
            if (uris.isEmpty()) {
                return@withContext Result.failure(IllegalStateException("No assets found to share"))
            }
            
            Log.d(TAG, "Ready to share ${uris.size} assets")
            Result.success(uris)
        } catch (e: Exception) {
            Log.e(TAG, "Error preparing assets for sharing: ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Create multi-share intent for multiple files
     */
    fun createMultiShareIntent(uris: List<Uri>, mimeType: String = "*/*"): Intent {
        return Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = mimeType
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
    
    /**
     * Get the APK file from the app's installation
     */
    private fun getApkFile(): File {
        val appInfo = context.applicationInfo
        return File(appInfo.sourceDir)
    }
    
    /**
     * Check if Bluetooth is available
     */
    fun isBluetoothAvailable(): Boolean {
        // Implementation would check Bluetooth adapter
        // For now, return true as placeholder
        return true
    }
    
    /**
     * Check if WiFi Direct is available
     */
    fun isWifiDirectAvailable(): Boolean {
        // Implementation would check WiFi P2P capabilities
        // For now, return true as placeholder
        return true
    }
    
    companion object {
        const val SHARE_ACTION = "com.hayat.action.SHARE_APP"
        const val EXTRA_SHARE_TYPE = "share_type"
        
        const val SHARE_TYPE_APK = "apk"
        const val SHARE_TYPE_DATABASE = "database"
        const val SHARE_TYPE_ALL = "all"
    }
}
