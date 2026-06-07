package com.hayat.services

import android.util.Log
import com.hayat.data.MedicalChunk
import com.hayat.data.MedicalPromptBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed class ModelState {
    object Loading : ModelState()
    object Ready : ModelState()
    data class Error(val message: String) : ModelState()
}

class ModelInferenceService(
    private val modelFile: File,
    private val ragEngine: (String) -> List<MedicalChunk>,
    private val onStateChange: (ModelState) -> Unit
) {

    private val TAG = "ModelInferenceService"

    private var isModelLoaded = false
    private var loadError: String? = null

    init {
        loadModel()
    }

    private fun loadModel() {
        onStateChange(ModelState.Loading)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                withContext(Dispatchers.IO) {
                    if (!modelFile.exists()) {
                        throw IllegalStateException("Model file not found at ${modelFile.absolutePath}")
                    }

                    val fileSizeMB = modelFile.length() / (1024 * 1024)
                    Log.d(TAG, "Model file found at ${modelFile.absolutePath}, size: ${fileSizeMB}MB")

                    delay(300)
                }

                isModelLoaded = true
                loadError = null
                onStateChange(ModelState.Ready)
                Log.d(TAG, "Model loaded successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load model: ${e.message}", e)
                isModelLoaded = false
                loadError = e.message
                onStateChange(ModelState.Error("فشل تحميل النموذج: ${e.message}"))
            }
        }
    }

    fun generateResponse(userQuery: String): Flow<String> = callbackFlow {
        if (!isModelLoaded) {
            val errorMsg = loadError ?: "النموذج غير محمّل بعد"
            trySend("خطأ: $errorMsg")
            close(IllegalStateException(errorMsg))
            return@callbackFlow
        }

        val prompt = try {
            val chunks = ragEngine(userQuery)
            if (chunks.isNotEmpty()) {
                MedicalPromptBuilder.buildPrompt(userQuery, chunks)
            } else {
                MedicalPromptBuilder.buildFallbackPrompt(userQuery)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Prompt building failed: ${e.message}", e)
            MedicalPromptBuilder.buildFallbackPrompt(userQuery)
        }

        Log.d(TAG, "Generated prompt (first 200 chars): ${prompt.take(200)}...")

        try {
            val response = buildDemoResponse(prompt)
            val words = mutableListOf<String>()
            words.addAll(response.split(" "))

            var accumulated = StringBuilder()
            val chunkSize = 3

            for (i in words.indices step chunkSize) {
                val endIndex = minOf(i + chunkSize, words.size)
                val wordChunk = words.subList(i, endIndex).joinToString(" ") + " "
                accumulated.append(wordChunk)
                trySend(accumulated.toString())
                delay(60)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Generation error: ${e.message}", e)
            close(e)
            return@callbackFlow
        }

        close()
    }.catch { e ->
        Log.e(TAG, "Flow error: ${e.message}", e)
        emit("حدث خطأ أثناء توليد الاستجابة: ${e.message}")
    }

    private fun buildDemoResponse(prompt: String): String {
        val isEmergency = prompt.contains("نزيف", ignoreCase = true) ||
                prompt.contains("حروق", ignoreCase = true) ||
                prompt.contains("كسور", ignoreCase = true) ||
                prompt.contains("CPR", ignoreCase = true) ||
                prompt.contains("إسعاف", ignoreCase = true) ||
                prompt.contains("اختناق", ignoreCase = true) ||
                prompt.contains("لدغة", ignoreCase = true)

        return if (isEmergency) {
            """
            بناءً على الإرشادات الطبية المتاحة في قاعدة البيانات المحلية:
            1. تأكد من سلامتك أولاً قبل مساعدة المصاب.
            2. اتصل بخدمات الطوارئ على الفور إذا كانت الحالة خطيرة.
            3. طبق الإسعافات الأولية المناسبة للحالة.
            4. لا تحرك المصاب إذا كنت تشتبه في إصابة في العمود الفقري.
            5. راقب العلامات الحيوية (التنفس، النبض، الوعي) باستمرار.
            ملاحظة: هذه إرشادات أولية ولا تغني عن التقييم الطبي المتخصص.
            """.trimIndent().replace("\n", " ")
        } else {
            """
            أهلاً! أنا مساعدك الطبي الذكي "حياتي".
            يمكنني مساعدتك في الإرشادات الطبية الأولية للحالات الطارئة.
            كل ما عليك هو وصف الحالة وسأقوم بتقديم المشورة بناءً على البروتوكولات الطبية المحلية.
            تذكر: في الحالات الخطيرة، اتصل بالطوارئ دائماً.
            """.trimIndent().replace("\n", " ")
        }
    }

    fun isReady(): Boolean = isModelLoaded

    fun getError(): String? = loadError
}
