package com.example.bilidownloader.di

import android.content.Context
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.data.database.AppDatabase
import com.example.bilidownloader.data.repository.*
import com.example.bilidownloader.domain.*

/**
 * 依赖注入容器接口
 */
interface AppContainer {
    // Repositories
    val historyRepository: HistoryRepository
    val userRepository: UserRepository
    val downloadRepository: DownloadRepository
    val subtitleRepository: SubtitleRepository // 新增
    val mediaRepository: MediaRepository

    // UseCases
    val analyzeVideoUseCase: AnalyzeVideoUseCase
    val downloadVideoUseCase: DownloadVideoUseCase
    val getSubtitleUseCase: GetSubtitleUseCase // 新增
    val prepareTranscribeUseCase: PrepareTranscribeUseCase
}

/**
 * 容器的具体实现
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    // 1. 数据库实例
    private val database by lazy {
        AppDatabase.getDatabase(context)
    }

    // 2. API Service
    private val biliApiService = NetworkModule.biliService

    // =========================================================
    // 3. Repository 初始化
    // =========================================================

    override val historyRepository by lazy {
        HistoryRepository(database.historyDao())
    }

    override val userRepository by lazy {
        UserRepository(database.userDao())
    }

    override val downloadRepository by lazy {
        DownloadRepository()
    }

    override val subtitleRepository by lazy {
        SubtitleRepository(biliApiService)
    }

    override val mediaRepository by lazy {
        MediaRepository(context)
    }

    // =========================================================
    // 4. UseCase 初始化
    // =========================================================

    override val analyzeVideoUseCase by lazy {
        AnalyzeVideoUseCase(historyRepository)
    }

    override val downloadVideoUseCase by lazy {
        DownloadVideoUseCase(context, downloadRepository)
    }

    override val getSubtitleUseCase by lazy {
        GetSubtitleUseCase(subtitleRepository, biliApiService)
    }

    // 【修正】这里补上了 downloadRepository 参数
    override val prepareTranscribeUseCase by lazy {
        PrepareTranscribeUseCase(context, downloadRepository)
    }
}