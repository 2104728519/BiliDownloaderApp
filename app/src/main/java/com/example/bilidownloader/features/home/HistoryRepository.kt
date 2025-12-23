package com.example.bilidownloader.features.home

import com.example.bilidownloader.core.database.HistoryDao
import com.example.bilidownloader.core.database.HistoryEntity
import kotlinx.coroutines.flow.Flow

/**
 * 历史记录仓库.
 * 封装对 [HistoryDao] 的数据库操作.
 */
class HistoryRepository(private val historyDao: HistoryDao) {

    /** 获取实时更新的历史记录流 */
    val allHistory: Flow<List<HistoryEntity>> = historyDao.getAllHistory()

    suspend fun insert(history: HistoryEntity) {
        historyDao.insertHistory(history)
    }

    suspend fun deleteList(histories: List<HistoryEntity>) {
        historyDao.deleteHistories(histories)
    }
}