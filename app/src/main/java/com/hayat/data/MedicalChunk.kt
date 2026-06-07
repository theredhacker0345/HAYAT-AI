package com.hayat.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

@Entity(tableName = "medical_chunks")
data class MedicalChunk(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val protocol_title: String,
    val content: String,
    val embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as MedicalChunk
        if (id != other.id) return false
        if (protocol_title != other.protocol_title) return false
        if (content != other.content) return false
        if (embedding == null && other.embedding == null) return true
        if (embedding == null || other.embedding == null) return false
        if (!embedding.contentEquals(other.embedding)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + protocol_title.hashCode()
        result = 31 * result + content.hashCode()
        result = 31 * result + (embedding?.contentHashCode() ?: 0)
        return result
    }
}

class FloatArrayConverter {
    @TypeConverter
    fun fromFloatArray(array: FloatArray): ByteArray {
        val byteBuffer = ByteBuffer.allocate(array.size * 4)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        for (value in array) {
            byteBuffer.putFloat(value)
        }
        return byteBuffer.array()
    }

    @TypeConverter
    fun toFloatArray(byteArray: ByteArray): FloatArray {
        val byteBuffer = ByteBuffer.wrap(byteArray)
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val floatArray = FloatArray(byteArray.size / 4)
        for (i in floatArray.indices) {
            floatArray[i] = byteBuffer.float
        }
        return floatArray
    }
}
