package com.example.bilidownloader.di

import android.content.Context
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.data.database.AppDatabase
import com.example.bilidownloader.data.repository.*
import com.example.bilidownloader.domain.*
import com.example.bilidownloader.domain.usecase.GenerateCommentUseCase // 【新增】
import com.example.bilidownloader.domain.usecase.PostCommentUseCase     // 【新增】

/**
 * 依赖注入容器接口
 */
interface AppContainer {
    // Repositories
    val historyRepository: HistoryRepository
    val userRepository: UserRepository
    val downloadRepository: DownloadRepository
    val subtitleRepository: SubtitleRepository
    val mediaRepository: MediaRepository
    val commentRepository: CommentRepository // 【新增】
    val llmRepository: LlmRepository         // 【新增】

    // UseCases
    val analyzeVideoUseCase: AnalyzeVideoUseCase
    val downloadVideoUseCase: DownloadVideoUseCase
    val getSubtitleUseCase: GetSubtitleUseCase
    val prepareTranscribeUseCase: PrepareTranscribeUseCase
    val generateCommentUseCase: GenerateCommentUseCase // 【新增】
    val postCommentUseCase: PostCommentUseCase         // 【新增】
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
    // 3. Repository 初始化 (使用 lazy 延迟加载)
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

    // 【新增】注册新的 Repository
    override val commentRepository by lazy {
        CommentRepository(context)
    }

    override val llmRepository by lazy {
        LlmRepository()
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

    override val prepareTranscribeUseCase by lazy {
        PrepareTranscribeUseCase(context, downloadRepository)
    }

    // 【新增】注册新的 UseCase
    override val generateCommentUseCase by lazy {
        GenerateCommentUseCase(llmRepository)
    }

    override val postCommentUseCase by lazy {
        PostCommentUseCase(commentRepository)
    }
}