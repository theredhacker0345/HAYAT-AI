package com.hayat.data

import androidx.room.Entity
import androidx.room.PrimaryKey

enum class ChatRole {
    USER, ASSISTANT
}

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val role: ChatRole,
    val contextChunks: List<MedicalChunk> = emptyList()
)
