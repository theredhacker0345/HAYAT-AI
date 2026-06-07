package com.hayat.ui.screens

import com.hayat.data.MedicalChunk

object MedicalPromptBuilder {
    private const val SYSTEM_PROMPT = """أنت مساعد طبي متوفر بالكامل في الموقع. مهمتك هي تقديم إرشادات طبية طوارئ دقيقة وآمنة. استخدم المعلومات من السياق أدناه للإجابة على أسئلة المستخدم. إذا كنت غير متأكد، اشترح التوجه إلى أقرب مستشفى. لا توصف أي أدوية للدواء بدون خيارات بديلة. اذكر مصادر المعلومات المقدمة في السياق.
"""

    fun buildPrompt(userQuery: String, contextChunks: List<MedicalChunk>): String {
        val contextBuilder = StringBuilder()
        
        if (contextChunks.isNotEmpty()) {
            contextBuilder.append("السياق الطبي:\n\n")
            contextChunks.forEachIndexed { index, chunk ->
                contextBuilder.append("${index + 1}. ${chunk.protocol_title}: ${chunk.content}\n\n")
            }
            contextBuilder.append("السؤال: $userQuery\n\nالإجابة:")
        } else {
            contextBuilder.append("السؤال: $userQuery\n\nالإجابة:")
        }

        return buildString {
            append(SYSTEM_PROMPT)
            append("\n\n")
            append(contextBuilder.toString())
        }
    }

    fun buildChatTemplate(messages: List<Pair<String, String>>): String {
        return buildString {
            messages.forEach { (role, content) ->
                when (role) {
                    "system" -> append("<|im_start|>system\n$content<|im_end|>\n")
                    "user" -> append("<|im_start|>user\n$content<|im_end|>\n")
                    "assistant" -> append("<|im_start|>assistant\n$content<|im_end|>\n")
                }
            }
            append("<|im_start|>assistant\n")
        }
    }
}