package com.example.bilidownloader.data.repository

import com.example.bilidownloader.data.database.HistoryDao
import com.example.bilidownloader.data.database.HistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 历史记录管理员
 * 专门负责跟数据库打交道
 */
class HistoryRepository(private val historyDao: HistoryDao) {

    // 1. 获取所有记录 (这是一个“活”的列表，数据库一变，这里自动变)
    val allHistory: Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    // 2. 记一笔 (插入/更新)
    suspend fun insert(history: HistoryEntity) {
        historyDao.insertHistory(history)
    }

    // 3. 删掉一些 (批量删除)
    suspend fun deleteList(histories: List<HistoryEntity>) {
        historyDao.deleteHistories(histories)
    }
}