package com.example.bilidownloader.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.bilidownloader.MyApplication
import com.example.bilidownloader.features.aicomment.AiCommentViewModel
import com.example.bilidownloader.features.ffmpeg.FfmpegViewModel
import com.example.bilidownloader.features.home.HomeViewModel
import com.example.bilidownloader.features.login.LoginViewModel
import com.example.bilidownloader.features.tools.audiocrop.AudioPickerViewModel
import com.example.bilidownloader.features.tools.transcription.TranscriptionViewModel

/**
 * 提供全应用 ViewModel 的工厂对象
 * 使用 [viewModelFactory] 构建器统一管理依赖注入
 */
object AppViewModelProvider {

    val Factory = viewModelFactory {

        // --- Home 模块 (原 MainViewModel) ---
        initializer {
            val app = bilidownloaderApplication()
            val container = app.container
            HomeViewModel(
                application = app,
                historyRepository = container.historyRepository,
                authRepository = container.authRepository,
                homeRepository = container.homeRepository,
                downloadRepository = container.downloadRepository,
                subtitleRepository = container.subtitleRepository
            )
        }

        // --- 登录模块 ---
        initializer {
            val app = bilidownloaderApplication()
            LoginViewModel(
                application = app,
                authRepository = app.container.authRepository
            )
        }

        // --- AI 评论分析模块 ---
        initializer {
            val container = bilidownloaderApplication().container
            AiCommentViewModel(
                homeRepository = container.homeRepository,
                subtitleRepository = container.subtitleRepository,
                llmRepository = container.llmRepository,
                commentRepository = container.commentRepository,
                styleRepository = container.styleRepository
            )
        }

        // --- FFmpeg 处理模块 (新增) ---
        initializer {
            val app = bilidownloaderApplication()
            FfmpegViewModel(
                application = app,
                repository = app.container.ffmpegRepository,
                savedStateHandle = createSavedStateHandle() // 注入 SavedStateHandle 以支持进程重启后的状态恢复
            )
        }

        // --- 工具箱：音频选择模块 ---
        initializer {
            AudioPickerViewModel(
                application = bilidownloaderApplication()
            )
        }

        // --- 工具箱：语音转写模块 ---
        initializer {
            TranscriptionViewModel(
                application = bilidownloaderApplication()
            )
        }
    }
}

/**
 * 扩展函数：从 [CreationExtras] 中安全获取 [MyApplication] 实例
 */
fun CreationExtras.bilidownloaderApplication(): MyApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MyApplication)