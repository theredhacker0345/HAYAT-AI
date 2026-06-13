package com.hayat.services

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader

class WordPieceTokenizer(private val context: Context) {
    private val TAG = "WordPieceTokenizer"
    private val vocab: Map<String, Int> = mutableMapOf()
    private val unkToken = "[UNK]"
    private val clsToken = "[CLS]"
    private val sepToken = "[SEP]"
    private val padToken = "[PAD]"

    init {
        loadVocab()
    }

    private fun loadVocab() {
        try {
            context.assets.open("vocab.json").use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    val jsonString = reader.readText()
                    val jsonObject = JSONObject(jsonString)
                    val vocabObject = jsonObject.getJSONObject("vocab")
                    val keys = vocabObject.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        (vocab as MutableMap<String, Int>)[key] = vocabObject.getInt(key)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading vocab: ${e.message}")
        }
    }

    fun tokenize(text: String, maxLength: Int = 128): IntArray {
        val normalizedText = text.lowercase().trim()
        val tokens = mutableListOf<Int>()

        tokens.add(vocab[clsToken] ?: vocab[unkToken] ?: 1)

        normalizedText.split("\\s+".toRegex()).forEach { word ->
            val subwords = tokenizeWord(word)
            tokens.addAll(subwords)
        }

        tokens.add(vocab[sepToken] ?: vocab[unkToken] ?: 1)

        while (tokens.size < maxLength) {
            tokens.add(vocab[padToken] ?: 0)
        }

        return tokens.take(maxLength).toIntArray()
    }

    private fun tokenizeWord(word: String): List<Int> {
        if (word.isEmpty()) return emptyList()

        val tokens = mutableListOf<Int>()
        var remaining = word

        while (remaining.isNotEmpty()) {
            val found = findLongestMatch(remaining)
            if (found != null) {
                tokens.add(found.first)
                remaining = remaining.substring(found.second)
            } else {
                tokens.add(vocab[unkToken] ?: 1)
                break
            }
        }

        return tokens
    }

    private fun findLongestMatch(word: String): Pair<Int, Int>? {
        for (i in word.length downTo 1) {
            val prefix = word.substring(0, i)
            val tokenId = vocab[prefix]
            if (tokenId != null) {
                return Pair(tokenId, i)
            }
        }
        return null
    }
}