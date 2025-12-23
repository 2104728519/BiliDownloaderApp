package com.example.bilidownloader.di

import android.content.Context
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.core.database.AppDatabase
import com.example.bilidownloader.data.repository.*
import com.example.bilidownloader.domain.usecase.*

/**
 * 依赖注入容器接口.
 * 定义应用中所有 Repository 和 UseCase 的单例获取方式.
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
    val styleRepository: StyleRepository

    // UseCases (业务逻辑单元)
    val analyzeVideoUseCase: AnalyzeVideoUseCase
    val downloadVideoUseCase: DownloadVideoUseCase
    val getSubtitleUseCase: GetSubtitleUseCase
    val prepareTranscribeUseCase: PrepareTranscribeUseCase
    val generateCommentUseCase: GenerateCommentUseCase
    val postCommentUseCase: PostCommentUseCase
    val getRecommendedVideosUseCase: GetRecommendedVideosUseCase
}

/**
 * 手动依赖注入容器的具体实现.
 * 使用 `lazy` 委托实现懒加载单例模式，仅在首次使用时初始化对象。
 */
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