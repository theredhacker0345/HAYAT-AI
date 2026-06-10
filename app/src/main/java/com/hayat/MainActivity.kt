package com.hayat

import android.app.LocaleManager
import android.content.Context
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hayat.data.ChatMessage
import com.hayat.data.ChatRole
import com.hayat.ui.screens.ChatScreen
import com.hayat.ui.screens.ErrorScreen
import com.hayat.ui.screens.SettingsScreen
import com.hayat.ui.theme.HayatTheme
import java.io.File

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setAppLanguage(getSavedLanguage())
        setContent {
            HayatApp()
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(updateBaseContext(newBase))
    }

    private fun updateBaseContext(context: Context): Context {
        val savedLanguage = getSavedLanguage()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val locales = LocaleList(java.util.Locale(savedLanguage))
            localeManager?.applicationLocales = locales
            return context.createConfigurationContext(locales.toConfiguration())
        }
        val config = Resources.Configuration()
        config.setLocale(java.util.Locale(savedLanguage))
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.createConfigurationContext(config)
        } else {
            context.resources.updateConfiguration(config, context.resources.displayMetrics)
            context
        }
    }

    private fun getSavedLanguage(): String {
        return getPreferences(Context.MODE_PRIVATE)
            .getString("app_language", "en") ?: "en"
    }

    fun setAppLanguage(language: String) {
        getPreferences(Context.MODE_PRIVATE).edit()
            .putString("app_language", language)
            .apply()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val localeManager = getSystemService(LocaleManager::class.java)
            localeManager?.applicationLocales = LocaleList(java.util.Locale(language))
        } else {
            AppCompatDelegate.setApplicationLocales(androidx.core.os.LocaleListCompat.forLanguageTags(language))
        }
    }
}

@Composable
fun HayatApp() {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Chat) }
    var assetsMissing by remember { mutableStateOf(false) }
    val hayatFolder = File(Environment.getExternalStorageDirectory(), "Hayat")

    LaunchedEffect(Unit) {
        val modelFile = File(hayatFolder, "model.gguf")
        val embeddingModelFile = File(hayatFolder, "e5-small.onnx")
        val dbFile = File(hayatFolder, "medical.db")

        if (!hayatFolder.exists() || !modelFile.exists() || !embeddingModelFile.exists() || !dbFile.exists()) {
            assetsMissing = true
        }
    }

    val messages = remember { mutableStateListOf<ChatMessage>() }
    var modelPath by remember { mutableStateOf(File(hayatFolder, "model.gguf").absolutePath) }
    var dbPath by remember { mutableStateOf(File(hayatFolder, "medical.db").absolutePath) }
    val activity = (LocalView.current.context as? MainActivity)

    HayatTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            if (assetsMissing) {
                ErrorScreen(missingPath = hayatFolder.absolutePath)
            } else {
                Box {
                    when (currentScreen) {
                        Screen.Chat -> ChatScreen(
                            messages = messages,
                            onSendMessage = { text ->
                                messages.add(ChatMessage(text, ChatRole.USER))
                                messages.add(
                                    ChatMessage(
                                        stringResource(com.hayat.R.string.chat_response_placeholder, text),
                                        ChatRole.ASSISTANT
                                    )
                                )
                            }
                        )
                        Screen.Settings -> SettingsScreen(
                            modelPath = modelPath,
                            dbPath = dbPath,
                            onPathsChanged = { newModel, newDb ->
                                modelPath = newModel
                                dbPath = newDb
                                currentScreen = Screen.Chat
                            },
                            onLanguageChanged = { language ->
                                activity?.setAppLanguage(language)
                                (LocalView.current.context as? ComponentActivity)?.recreate()
                            }
                        )
                    }

                    FloatingActionButton(
                        onClick = {
                            currentScreen = if (currentScreen == Screen.Chat) Screen.Settings else Screen.Chat
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(16.dp),
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(com.hayat.R.string.content_description_toggle_settings),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

sealed class Screen {
    object Chat : Screen()
    object Settings : Screen()
}
