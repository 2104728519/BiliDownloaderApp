package com.example.bilidownloader.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * 浏览/解析历史 DAO.
 */
@Dao
interface HistoryDao {

    /**
     * 插入历史记录.
     * 使用 REPLACE 策略：若 BV 号已存在，则更新时间戳，将其顶到列表最前.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    /**
     * 获取所有历史记录.
     * 返回 Flow 以实现 UI 的实时响应式更新.
     */
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    @Delete
    suspend fun deleteHistory(history: HistoryEntity)

    @Delete
    suspend fun deleteHistories(histories: List<HistoryEntity>)
}