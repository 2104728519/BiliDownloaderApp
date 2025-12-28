package com.example.bilidownloader.di

import android.content.Context
import com.example.bilidownloader.core.database.AppDatabase
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.features.aicomment.CommentRepository
import com.example.bilidownloader.features.aicomment.LlmRepository
import com.example.bilidownloader.features.aicomment.StyleRepository
import com.example.bilidownloader.features.ffmpeg.FfmpegRepository
import com.example.bilidownloader.features.home.DownloadRepository
import com.example.bilidownloader.features.home.HistoryRepository
import com.example.bilidownloader.features.home.HomeRepository
import com.example.bilidownloader.features.home.SubtitleRepository
import com.example.bilidownloader.features.login.AuthRepository
import com.example.bilidownloader.features.tools.audiocrop.MediaRepository

/**
 * 依赖注入容器接口.
 * 定义应用中所有 Repository 的单例获取方式.
 * 遵循逻辑下沉原则，业务逻辑由 Repository 层承担.
 */
interface AppContainer {
    // --- 核心业务 Repository ---
    val homeRepository: HomeRepository       // 视频解析与推荐流
    val downloadRepository: DownloadRepository // 下载管理
    val historyRepository: HistoryRepository   // 历史记录
    val authRepository: AuthRepository         // 认证与用户信息
    val subtitleRepository: SubtitleRepository // 字幕处理

    // --- AI 评论功能 Repository ---
    val commentRepository: CommentRepository
    val llmRepository: LlmRepository
    val styleRepository: StyleRepository

    // --- 工具与媒体处理 Repository ---
    val ffmpegRepository: FfmpegRepository   // FFmpeg 视频处理
    val mediaRepository: MediaRepository     // 媒体资源访问
}

/**
 * 手动依赖注入容器的具体实现.
 * 使用 `lazy` 委托实现懒加载单例模式，确保资源仅在需要时初始化.
 */
class DefaultAppContainer(private val context: Context) : AppContainer {

    // region 基础组件 (Infrastructure)

    private val database by lazy {
        AppDatabase.getDatabase(context)
    }

    private val biliApiService = NetworkModule.biliService

    private val commentedVideoDao by lazy {
        database.commentedVideoDao()
    }

    // endregion

    // region Repository 实例化实现

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
        // HomeRepository 聚合了推荐流获取和视频解析功能，需访问历史记录 DAO
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

    override val ffmpegRepository by lazy {
        FfmpegRepository(context)
    }

    override val mediaRepository by lazy {
        MediaRepository(context)
    }

    // endregion
}