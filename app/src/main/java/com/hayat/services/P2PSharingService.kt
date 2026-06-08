package com.hayat.services

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import android.util.Log
import java.io.File

class P2PSharingService private constructor(private val context: Context) {

    companion object {
        @Volatile
        private var INSTANCE: P2PSharingService? = null

        fun getInstance(context: Context): P2PSharingService =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: P2PSharingService(context.applicationContext).also { INSTANCE = it }
            }

        const val HAYAT_FOLDER = "/sdcard/Hayat/"
        const val APK_OUTPUT_PATH = "/sdcard/Hayat/HayatAI.apk"
    }

    fun exportApk(): Boolean {
        return try {
            val hayatFolder = File(HAYAT_FOLDER)
            if (!hayatFolder.exists()) {
                hayatFolder.mkdirs()
            }

            val apkFile = File(APK_OUTPUT_PATH)
            
            // Get the actual APK path from installed package
            val apkPath = getInstalledApkPath()
            if (apkPath != null) {
                java.io.FileInputStream(apkPath).use { input ->
                    java.io.FileOutputStream(apkFile).use { output ->
                        input.copyTo(output)
                    }
                }
            } else {
                Log.w("P2PSharingService", "Could not find installed APK path, skipping export")
                return false
            }
            true
        } catch (e: Exception) {
            Log.e("P2PSharingService", "Failed to export APK", e)
            false
        }
    }
    
    private fun getInstalledApkPath(): String? {
        return try {
            val packageName = context.packageName
            val packageInfo = context.packageManager.getPackageInfo(packageName, PackageManager.GET_SOURCE_DIR)
            packageInfo.applicationInfo.sourceDir
        } catch (e: Exception) {
            Log.e("P2PSharingService", "Failed to get APK path", e)
            null
        }
    }

    fun shareApk(): Boolean {
        return try {
            val apkFile = File(APK_OUTPUT_PATH)
            if (!apkFile.exists()) {
                // Try to export first
                if (!exportApk()) {
                    return false
                }
            }

            val apkUri = Uri.fromFile(apkFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val bluetoothIntent = Intent(shareIntent).apply {
                setPackage("com.android.bluetooth")
            }

            val wifiDirectIntent = Intent(shareIntent).apply {
                setPackage("com.android.wifidirect")
            }

            // Create chooser with specific options
            val chooserIntent = Intent.createChooser(shareIntent, "مشاركة التطبيق").apply {
                // Add Bluetooth and WiFi Direct as explicit targets
                putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(
                    bluetoothIntent,
                    wifiDirectIntent
                ))
            }

            context.startActivity(chooserIntent)
            true
        } catch (e: Exception) {
            Log.e("P2PSharingService", "Failed to share APK", e)
            false
        }
    }

    fun shareApkSimple(): Boolean {
        return try {
            val apkFile = File(APK_OUTPUT_PATH)
            if (!apkFile.exists()) {
                if (!exportApk()) {
                    return false
                }
            }

            val apkUri = Uri.fromFile(apkFile)

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, apkUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            context.startActivity(Intent.createChooser(intent, "مشاركة التطبيق عبر Bluetooth/WiFi Direct"))
            true
        } catch (e: Exception) {
            Log.e("P2PSharingService", "Failed to share APK", e)
            false
        }
    }
}