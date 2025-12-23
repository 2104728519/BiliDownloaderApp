package com.example.bilidownloader.di

import android.content.Context
import com.example.bilidownloader.core.database.AppDatabase
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.data.repository.*
import com.example.bilidownloader.domain.usecase.*
import com.example.bilidownloader.features.login.AuthRepository // 新引用

interface AppContainer {
    // Repositories
    val historyRepository: HistoryRepository
    val authRepository: AuthRepository // 重命名
    val downloadRepository: DownloadRepository
    val subtitleRepository: SubtitleRepository
    val mediaRepository: MediaRepository
    val commentRepository: CommentRepository
    val llmRepository: LlmRepository
    val recommendRepository: RecommendRepository
    val styleRepository: StyleRepository

    // UseCases
    val analyzeVideoUseCase: AnalyzeVideoUseCase
    val downloadVideoUseCase: DownloadVideoUseCase
    val getSubtitleUseCase: GetSubtitleUseCase
    val prepareTranscribeUseCase: PrepareTranscribeUseCase
    val generateCommentUseCase: GenerateCommentUseCase
    val postCommentUseCase: PostCommentUseCase
    val getRecommendedVideosUseCase: GetRecommendedVideosUseCase
}

class DefaultAppContainer(private val context: Context) : AppContainer {

    private val database by lazy {
        AppDatabase.getDatabase(context)
    }

    private val biliApiService = NetworkModule.biliService

    private val commentedVideoDao by lazy {
        database.commentedVideoDao()
    }

    // region Repositories

    override val historyRepository by lazy {
        HistoryRepository(database.historyDao())
    }

    // 更新 AuthRepository 的实例化
    override val authRepository by lazy {
        AuthRepository(context, database.userDao())
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

    override val styleRepository by lazy {
        StyleRepository(database.customStyleDao())
    }

    // endregion

    // region UseCases

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

    // endregion
}