package com.example.bilidownloader.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.bilidownloader.MyApplication
import com.example.bilidownloader.ui.viewmodel.AiCommentViewModel
import com.example.bilidownloader.ui.viewmodel.AudioPickerViewModel
import com.example.bilidownloader.ui.viewmodel.MainViewModel

/**
 * ViewModel 工厂
 * 负责把 Repository 和 UseCase 注入到 ViewModel 中
 */
object AppViewModelProvider {
    val Factory = viewModelFactory {

        // 1. 配置 MainViewModel
        initializer {
            val container = bilidownloaderApplication().container
            MainViewModel(
                application = bilidownloaderApplication(),
                historyRepository = container.historyRepository,
                userRepository = container.userRepository,
                analyzeVideoUseCase = container.analyzeVideoUseCase,
                downloadVideoUseCase = container.downloadVideoUseCase,
                prepareTranscribeUseCase = container.prepareTranscribeUseCase,
                getSubtitleUseCase = container.getSubtitleUseCase
            )
        }

        // 2. 配置 AudioPickerViewModel
        initializer {
            AudioPickerViewModel(
                application = bilidownloaderApplication(),
            )
        }

        // =========================================================
        // 3. 【修改】注册 AiCommentViewModel
        // =========================================================
        initializer {
            val container = bilidownloaderApplication().container
            AiCommentViewModel(
                analyzeVideoUseCase = container.analyzeVideoUseCase,
                getSubtitleUseCase = container.getSubtitleUseCase,
                generateCommentUseCase = container.generateCommentUseCase,
                postCommentUseCase = container.postCommentUseCase,
                getRecommendedVideosUseCase = container.getRecommendedVideosUseCase,
                recommendRepository = container.recommendRepository,
                // [新增] 注入风格仓库，实现自定义风格的持久化
                styleRepository = container.styleRepository
            )
        }
    }
}

/**
 * 扩展函数：方便从 CreationExtras 中获取 MyApplication 实例
 */
fun CreationExtras.bilidownloaderApplication(): MyApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MyApplication)