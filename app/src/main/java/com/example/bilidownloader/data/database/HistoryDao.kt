package com.example.bilidownloader.data.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    // 1. 记账 (插入或更新)
    // OnConflictStrategy.REPLACE: 如果 BV 号已经存在了，就用新的覆盖旧的 (比如更新了时间)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: HistoryEntity)

    // 2. 查账 (获取所有)
    // ORDER BY timestamp DESC: 按时间倒序排列 (最新的在最上面)
    // 返回 Flow: 这是一个“活”的列表，数据库一变，UI 自动跟着变
    @Query("SELECT * FROM history ORDER BY timestamp DESC")
    fun getAllHistory(): Flow<List<HistoryEntity>>

    // 3. 删账 (删除特定的记录)
    @Delete
    suspend fun deleteHistory(history: HistoryEntity)

    // 4. 批量删账 (多选删除用)
    @Delete
    suspend fun deleteHistories(histories: List<HistoryEntity>)
}