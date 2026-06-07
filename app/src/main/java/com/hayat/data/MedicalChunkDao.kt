package com.hayat.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface MedicalChunkDao {
    @Query("SELECT * FROM medical_chunks")
    suspend fun getAllChunks(): List<MedicalChunk>

    @Insert
    suspend fun insertChunk(chunk: MedicalChunk)
}
