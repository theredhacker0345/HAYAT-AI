package com.hayat.services

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.io.InputStream
import java.util.Locale

class WordPieceTokenizer(
    private val maxTokens: Int = MAX_TOKENS,
    private val vocabStream: InputStream? = null,
    private val context: Context? = null,
    private val assetManager: AssetManager? = null,
) {
    private val vocab: Map<String, Int> = loadVocab()
    private val clsTokenId: Int = vocab.getValue(CLS_TOKEN)
    private val padTokenId: Int = vocab.getValue(PAD_TOKEN)
    private val unkTokenId: Int = vocab.getValue(UNK_TOKEN)

    fun tokenize(text: String): List<Int> {
        val tokens = mutableListOf<Int>(clsTokenId)
        val normalized = normalizeText(text)

        if (normalized.isBlank() || tokens.size >= maxTokens) {
            return pad(tokens)
        }

        for (word in normalized.split(WHITESPACE_REGEX)) {
            if (word.isBlank()) continue

            for (tokenId in tokenizeWord(word)) {
                if (tokens.size >= maxTokens) return tokens
                tokens.add(tokenId)
            }
        }

        return pad(tokens)
    }

    private fun loadVocab(): Map<String, Int> {
        val streams = mutableListOf<InputStream>()

        context?.let { currentContext ->
            runCatching { streams += currentContext.assets.open(VOCAB_FILE_NAME) }
        }

        assetManager?.let { currentAssetManager ->
            runCatching { streams += currentAssetManager.open(VOCAB_FILE_NAME) }
        }

        vocabStream?.let { streams += it }

        VOCAB_FILE_CANDIDATES
            .asSequence()
            .filter { it.isFile }
            .map { it.inputStream() }
            .forEach { streams += it }

        for (stream in streams) {
            stream.use { currentStream ->
                val parsedVocab = parseVocabJson(currentStream.bufferedReader().readText())
                if (parsedVocab.isNotEmpty()) return parsedVocab
            }
        }

        return fallbackVocab()
    }

    private fun parseVocabJson(json: String): Map<String, Int> {
        val parsed = linkedMapOf<String, Int>()
        val matcher = VOCAB_ENTRY_REGEX.findAll(json)

        for (match in matcher) {
            val token = unescapeJson(match.groupValues[1])
            val tokenId = match.groupValues[2].toIntOrNull() ?: continue
            if (token !in parsed) {
                parsed[token] = tokenId
            }
        }

        return parsed
    }

    private fun unescapeJson(value: String): String {
        val result = StringBuilder(value.length)
        var index = 0

        while (index < value.length) {
            val current = value[index]
            var consumedCharacters = 2

            if (current != '\\' || index == value.length - 1) {
                result.append(current)
                index++
                continue
            }

            val next = value[index + 1]
            when (next) {
                '"', '\\', '/' -> result.append(next)
                'b' -> result.append('\b')
                'f' -> result.append('\u000C')
                'n' -> result.append('\n')
                'r' -> result.append('\r')
                't' -> result.append('\t')
                'u' -> {
                    if (index + 5 < value.length) {
                        val code = value.substring(index + 2, index + 6).toIntOrNull(16)
                        if (code != null) {
                            result.append(code.toChar())
                            consumedCharacters = 6
                        } else {
                            result.append(next)
                        }
                    } else {
                        result.append(next)
                    }
                }
                else -> result.append(next)
            }
            index += consumedCharacters
        }

        return result.toString()
    }

    private fun normalizeText(input: String): String {
        return input
            .lowercase(Locale.ROOT)
            .replace(NON_ALPHANUMERIC_REGEX, " ")
            .trim()
    }

    private fun tokenizeWord(word: String): List<Int> {
        if (word in vocab) {
            return listOf(vocab.getValue(word))
        }

        val tokenIds = mutableListOf<Int>()
        var start = 0

        while (start < word.length) {
            var end = word.length
            var currentTokenId: Int? = null

            while (start < end) {
                val piece = word.substring(start, end)
                val wordPiece = if (start == 0) piece else "##$piece"
                val tokenId = vocab[wordPiece]

                if (tokenId != null) {
                    currentTokenId = tokenId
                    break
                }

                end--
            }

            if (currentTokenId == null) {
                return listOf(unkTokenId)
            }

            tokenIds += currentTokenId
            start = end
        }

        return tokenIds
    }

    private fun pad(tokens: MutableList<Int>): List<Int> {
        while (tokens.size < maxTokens) {
            tokens += padTokenId
        }
        return tokens
    }

    private fun fallbackVocab(): Map<String, Int> = linkedMapOf(
        PAD_TOKEN to 0,
        UNK_TOKEN to 1,
        CLS_TOKEN to 2,
        SEP_TOKEN to 3,
        "query" to 4,
        ":" to 5,
        "hello" to 6,
        "medical" to 7,
        "medicine" to 8,
        "patient" to 9,
        "symptom" to 10,
        "##s" to 11,
        "pain" to 12,
        "fever" to 13,
        "cough" to 14,
        "diagnosis" to 15,
        "treatment" to 16,
        "blood" to 17,
        "pressure" to 18,
        "diabetes" to 19,
        "asthma" to 20,
        "cancer" to 21,
        "infection" to 22,
        "dose" to 23,
        "dosage" to 24,
        "allergy" to 25,
        "allergic" to 26,
        "heart" to 27,
        "stroke" to 28,
        "emergency" to 29,
        "care" to 30,
        "acute" to 31,
        "chronic" to 32,
        "the" to 33,
        "a" to 34,
        "and" to 35,
        "to" to 36,
        "of" to 37,
        "for" to 38,
        "with" to 39,
        "without" to 40,
        "rate" to 41,
        "headache" to 42,
        "nausea" to 43,
        "vomit" to 44,
        "diarrhea" to 45,
        "temperature" to 46,
        "hospital" to 47,
        "doctor" to 48,
        "nurse" to 49,
    )

    companion object {
        const val MAX_TOKENS = 128
        private const val VOCAB_FILE_NAME = "vocab.json"
        private const val PAD_TOKEN = "[PAD]"
        private const val UNK_TOKEN = "[UNK]"
        private const val CLS_TOKEN = "[CLS]"
        private const val SEP_TOKEN = "[SEP]"
        private val VOCAB_FILE_CANDIDATES = listOf(
            File("app/src/main/assets/$VOCAB_FILE_NAME"),
            File("src/main/assets/$VOCAB_FILE_NAME"),
            File("assets/$VOCAB_FILE_NAME"),
        )
        private val NON_ALPHANUMERIC_REGEX = Regex("[^a-z0-9\\s]")
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val VOCAB_ENTRY_REGEX = Regex("\"((?:\\\\.|[^\"])*)\"\\s*:\\s*(-?\\d+)")
    }
}
