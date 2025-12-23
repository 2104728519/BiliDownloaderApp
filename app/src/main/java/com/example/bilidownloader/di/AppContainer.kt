package com.example.bilidownloader.di

import android.content.Context
import com.example.bilidownloader.core.database.AppDatabase
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.features.tools.audiocrop.MediaRepository
import com.example.bilidownloader.features.aicomment.CommentRepository
import com.example.bilidownloader.features.aicomment.LlmRepository
import com.example.bilidownloader.features.aicomment.StyleRepository
import com.example.bilidownloader.features.home.DownloadRepository
import com.example.bilidownloader.features.home.HistoryRepository
import com.example.bilidownloader.features.home.HomeRepository
import com.example.bilidownloader.features.home.SubtitleRepository
import com.example.bilidownloader.features.login.AuthRepository

/**
 * 依赖注入容器接口.
 * 定义应用中所有 Repository 的单例获取方式.
 * (UseCase 已被移除，逻辑下沉至 Repository)
 */
interface AppContainer {
    // --- Features Repositories ---
    val historyRepository: HistoryRepository
    val authRepository: AuthRepository
    val downloadRepository: DownloadRepository
    val subtitleRepository: SubtitleRepository
    val homeRepository: HomeRepository // 原 RecommendRepository，现兼顾解析与推荐
    val commentRepository: CommentRepository
    val llmRepository: LlmRepository
    val styleRepository: StyleRepository

    // --- Data Repositories (待移动) ---
    val mediaRepository: MediaRepository
}

/**
 * 手动依赖注入容器的具体实现.
 * 使用 `lazy` 委托实现懒加载单例模式.
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    // Database & Network (Infrastructure)
    private val database by lazy {
        AppDatabase.getDatabase(context)
    }

    private val biliApiService = NetworkModule.biliService

    private val commentedVideoDao by lazy {
        database.commentedVideoDao()
    }

    // region Repository Implementations

    override val historyRepository by lazy {
        HistoryRepository(database.historyDao())
    }

    override val authRepository by lazy {
        AuthRepository(context, database.userDao())
    }

    override val downloadRepository by lazy {
        DownloadRepository(context)
    }

    override val subtitleRepository by lazy {
        SubtitleRepository(biliApiService)
    }

    override val homeRepository by lazy {
        // HomeRepository 聚合了推荐流获取和视频解析功能，因此需要访问历史记录 DAO
        HomeRepository(context, commentedVideoDao, database.historyDao())
    }

    override val commentRepository by lazy {
        CommentRepository(context)
    }

    override val llmRepository by lazy {
        LlmRepository()
    }

    override val styleRepository by lazy {
        StyleRepository(database.customStyleDao())
    }

    override val mediaRepository by lazy {
        MediaRepository(context)
    }

    // endregion
}