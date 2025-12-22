package com.example.bilidownloader.di

import android.content.Context
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.data.database.AppDatabase
import com.example.bilidownloader.data.repository.*
import com.example.bilidownloader.domain.*
import com.example.bilidownloader.domain.usecase.GenerateCommentUseCase
import com.example.bilidownloader.domain.usecase.PostCommentUseCase
import com.example.bilidownloader.domain.usecase.GetRecommendedVideosUseCase

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
    val commentRepository: CommentRepository
    val llmRepository: LlmRepository
    val recommendRepository: RecommendRepository
    val styleRepository: StyleRepository // [新增] 自定义风格仓库

    // UseCases
    val analyzeVideoUseCase: AnalyzeVideoUseCase
    val downloadVideoUseCase: DownloadVideoUseCase
    val getSubtitleUseCase: GetSubtitleUseCase
    val prepareTranscribeUseCase: PrepareTranscribeUseCase
    val generateCommentUseCase: GenerateCommentUseCase
    val postCommentUseCase: PostCommentUseCase
    val getRecommendedVideosUseCase: GetRecommendedVideosUseCase
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

    // 3. 数据库 DAO
    private val commentedVideoDao by lazy {
        database.commentedVideoDao()
    }

    // =========================================================
    // 4. Repository 初始化 (使用 lazy 延迟加载)
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

    override val commentRepository by lazy {
        CommentRepository(context)
    }

    override val llmRepository by lazy {
        LlmRepository()
    }

    override val recommendRepository by lazy {
        RecommendRepository(context, commentedVideoDao)
    }

    /**
     * [新增] 初始化自定义风格仓库
     * 传入数据库中注册的 customStyleDao
     */
    override val styleRepository by lazy {
        StyleRepository(database.customStyleDao())
    }

    // =========================================================
    // 5. UseCase 初始化
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

    override val generateCommentUseCase by lazy {
        GenerateCommentUseCase(llmRepository)
    }

    override val postCommentUseCase by lazy {
        PostCommentUseCase(commentRepository)
    }

    override val getRecommendedVideosUseCase by lazy {
        GetRecommendedVideosUseCase(
            recommendRepository,
            subtitleRepository,
            biliApiService
        )
    }
}