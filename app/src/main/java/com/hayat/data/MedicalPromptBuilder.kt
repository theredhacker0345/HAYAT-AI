package com.hayat.data

object MedicalPromptBuilder {

    private const val SYSTEM_PROMPT_ARABIC = """
أنت "حياتي"، مساعد طبي طارئ ذكي. مهمتك الأساسية هي تقديم إرشادات أولية للحالات الطبية العاجلة ONLY بناءً على السياق الطبي المذكور أدناه.
تعليمات صارمة:
1. استخدم ONLY المعلومات الموجودة في قسم "السياق الطبي" للإجابة.
2. إذا لم تجد إجابة في السياق، قل بوضوح: "عذراً، لا أستطيع العثور على معلومات طبية موثوقة لهذا الاستفسار في قاعدة البيانات المحلية."
3. لا تقدم تشخيصاً طبياً نهائياً ولا تصف أدوية محددة بجرعات.
4. دائماً أوصي بطلب المساعدة من طبيب مختص أو الاتصال بالخدمات الطبية الطارئة عند الحاجة.
5. كن مختصراً، واضحاً، وموجهاً للإجراءات العملية الفورية.
6. استخدم لغة عربية فصحى مفهومة.
""".trimIndent().replace("\n", " ")

    private const val CONTEXT_LABEL = "\n\nContext: "
    private const val QUESTION_LABEL = "\n\nQuestion: "
    private const val ANSWER_LABEL = "\nAnswer: "

    fun buildPrompt(
        userQuery: String,
        ragChunks: List<MedicalChunk>,
        maxContextLength: Int = 1500
    ): String {
        val contextText = ragChunks.joinToString("\n---\n") { it.content }.take(maxContextLength)

        return buildString {
            append(SYSTEM_PROMPT_ARABIC)
            if (contextText.isNotBlank()) {
                append(CONTEXT_LABEL)
                append(contextText)
            }
            append(QUESTION_LABEL)
            append(userQuery)
            append(ANSWER_LABEL)
        }
    }

    fun buildFallbackPrompt(userQuery: String): String {
        return buildString {
            append(SYSTEM_PROMPT_ARABIC)
            append(QUESTION_LABEL)
            append(userQuery)
            append(ANSWER_LABEL)
        }
    }
}
