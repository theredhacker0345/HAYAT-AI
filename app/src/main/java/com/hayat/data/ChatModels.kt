package com.hayat.data

enum class ChatRole {
    USER, ASSISTANT
}

data class ChatMessage(
    val content: String,
    val role: ChatRole,
    val source: String? = null
)
