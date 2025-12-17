package com.example.bilidownloader.di

import android.content.Context
import com.example.bilidownloader.data.database.AppDatabase
import com.example.bilidownloader.data.repository.DownloadRepository
import com.example.bilidownloader.data.repository.HistoryRepository
import com.example.bilidownloader.data.repository.MediaRepository
import com.example.bilidownloader.data.repository.UserRepository

/**
 * 依赖注入容器接口
 */
interface AppContainer {
    val historyRepository: HistoryRepository
    val userRepository: UserRepository
    val mediaRepository: MediaRepository
    val downloadRepository: DownloadRepository
}

/**
 * 具体的容器实现
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    // 1. 数据库单例 (懒加载)
    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    // 2. 仓库单例
    override val historyRepository: HistoryRepository by lazy {
        HistoryRepository(database.historyDao())
    }

    override val userRepository: UserRepository by lazy {
        UserRepository(database.userDao())
    }

    override val mediaRepository: MediaRepository by lazy {
        MediaRepository(context)
    }

    override val downloadRepository: DownloadRepository by lazy {
        DownloadRepository()
    }
}