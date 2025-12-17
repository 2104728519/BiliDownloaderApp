package com.example.bilidownloader.di

import android.content.Context
import com.example.bilidownloader.data.database.AppDatabase
import com.example.bilidownloader.data.repository.DownloadRepository
import com.example.bilidownloader.data.repository.HistoryRepository
import com.example.bilidownloader.data.repository.MediaRepository
import com.example.bilidownloader.data.repository.UserRepository
import com.example.bilidownloader.domain.AnalyzeVideoUseCase
import com.example.bilidownloader.domain.DownloadVideoUseCase
import com.example.bilidownloader.domain.PrepareTranscribeUseCase // 【新增】导入

/**
 * 依赖注入容器接口
 */
interface AppContainer {
    val historyRepository: HistoryRepository
    val userRepository: UserRepository
    val mediaRepository: MediaRepository
    val downloadRepository: DownloadRepository

    // 解析视频业务逻辑用例
    val analyzeVideoUseCase: AnalyzeVideoUseCase

    // 下载视频用例
    val downloadVideoUseCase: DownloadVideoUseCase

    // 【新增】转写准备用例
    val prepareTranscribeUseCase: PrepareTranscribeUseCase
}

/**
 * 具体的容器实现
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    // 1. 数据库单例 (懒加载)
    private val database: AppDatabase by lazy {
        AppDatabase.getDatabase(context)
    }

    // 2. 仓库单例 (Repositories)
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

    // 3. 业务用例 (UseCases)

    // 解析视频用例
    override val analyzeVideoUseCase: AnalyzeVideoUseCase by lazy {
        AnalyzeVideoUseCase(historyRepository)
    }

    // 下载视频用例
    override val downloadVideoUseCase: DownloadVideoUseCase by lazy {
        DownloadVideoUseCase(context, downloadRepository)
    }

    // 【新增】转写准备用例
    override val prepareTranscribeUseCase: PrepareTranscribeUseCase by lazy {
        PrepareTranscribeUseCase(context, downloadRepository)
    }
}