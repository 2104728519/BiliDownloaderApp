package com.example.bilidownloader.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 自定义评论风格 DAO.
 * 管理用户创建的 AI 提示词模板.
 */
@Dao
interface CustomStyleDao {
    @Query("SELECT * FROM custom_styles ORDER BY id DESC")
    fun getAllStyles(): Flow<List<CustomStyleEntity>>

    @Insert
    suspend fun insert(style: CustomStyleEntity)

    @Delete
    suspend fun delete(style: CustomStyleEntity)
}