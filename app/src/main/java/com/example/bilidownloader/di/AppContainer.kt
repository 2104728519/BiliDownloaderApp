package com.example.bilidownloader.di

import android.content.Context
import com.example.bilidownloader.core.database.AppDatabase
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.data.repository.*
import com.example.bilidownloader.domain.usecase.*
import com.example.bilidownloader.features.login.AuthRepository
import com.example.bilidownloader.features.aicomment.CommentRepository // 新引用
import com.example.bilidownloader.features.aicomment.LlmRepository // 新引用
import com.example.bilidownloader.features.aicomment.StyleRepository // 新引用

interface AppContainer {
    // Repositories
    val historyRepository: HistoryRepository
    val authRepository: AuthRepository
    val downloadRepository: DownloadRepository
    val subtitleRepository: SubtitleRepository
    val mediaRepository: MediaRepository
    val commentRepository: CommentRepository
    val llmRepository: LlmRepository
    val recommendRepository: RecommendRepository
    val styleRepository: StyleRepository

    // UseCases (仅保留尚未重构的)
    val analyzeVideoUseCase: AnalyzeVideoUseCase
    val downloadVideoUseCase: DownloadVideoUseCase
    val prepareTranscribeUseCase: PrepareTranscribeUseCase
    // 已删除: getSubtitleUseCase, generateCommentUseCase, postCommentUseCase, getRecommendedVideosUseCase
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

    override val prepareTranscribeUseCase by lazy {
        PrepareTranscribeUseCase(context, downloadRepository)
    }


    // endregion
}