package com.example.bilidownloader.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.bilidownloader.MyApplication
import com.example.bilidownloader.features.aicomment.AiCommentViewModel
import com.example.bilidownloader.features.home.HomeViewModel
import com.example.bilidownloader.features.login.LoginViewModel
import com.example.bilidownloader.features.tools.audiocrop.AudioPickerViewModel
import com.example.bilidownloader.features.tools.transcription.TranscriptionViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {

        // HomeViewModel (原 MainViewModel)
        initializer {
            val container = bilidownloaderApplication().container
            HomeViewModel(
                application = bilidownloaderApplication(),
                historyRepository = container.historyRepository,
                authRepository = container.authRepository,
                homeRepository = container.homeRepository,         // 核心数据源 (解析+推荐)
                downloadRepository = container.downloadRepository, // 下载核心
                subtitleRepository = container.subtitleRepository
            )
        }

        // LoginViewModel
        initializer {
            val container = bilidownloaderApplication().container
            LoginViewModel(
                application = bilidownloaderApplication(),
                authRepository = container.authRepository
            )
        }

        // AiCommentViewModel
        initializer {
            val container = bilidownloaderApplication().container
            AiCommentViewModel(
                homeRepository = container.homeRepository,         // 替代了 AnalyzeVideoUseCase 和 RecommendRepository
                subtitleRepository = container.subtitleRepository,
                llmRepository = container.llmRepository,
                commentRepository = container.commentRepository,
                styleRepository = container.styleRepository
            )
        }

        // AudioPickerViewModel (工具箱-音频选择)
        initializer {
            AudioPickerViewModel(
                application = bilidownloaderApplication(),
            )
        }

        // TranscriptionViewModel (工具箱-转写)
        initializer {
            TranscriptionViewModel(
                application = bilidownloaderApplication()
            )
        }
    }
}

/**
 * 扩展函数：便捷获取 Application 实例
 */
fun CreationExtras.bilidownloaderApplication(): MyApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MyApplication)