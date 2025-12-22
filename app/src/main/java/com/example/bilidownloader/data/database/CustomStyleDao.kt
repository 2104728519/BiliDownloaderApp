package com.example.bilidownloader.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomStyleDao {
    @Query("SELECT * FROM custom_styles ORDER BY id DESC")
    fun getAllStyles(): Flow<List<CustomStyleEntity>>

    @Insert
    suspend fun insert(style: CustomStyleEntity)

    @Delete
    suspend fun delete(style: CustomStyleEntity)
}