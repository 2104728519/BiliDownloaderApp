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
 * ViewModel 工厂配置.
 *
 * 负责在 ViewModel 创建时注入所需的 Repository 和 UseCase。
 * 替代了 Hilt/Dagger 的自动注入功能，适用于手动 DI 架构。
 */
object AppViewModelProvider {
    val Factory = viewModelFactory {

        // MainViewModel 注入配置
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

        // AudioPickerViewModel 注入配置
        initializer {
            AudioPickerViewModel(
                application = bilidownloaderApplication(),
            )
        }

        // AiCommentViewModel 注入配置
        initializer {
            val container = bilidownloaderApplication().container
            AiCommentViewModel(
                analyzeVideoUseCase = container.analyzeVideoUseCase,
                getSubtitleUseCase = container.getSubtitleUseCase,
                generateCommentUseCase = container.generateCommentUseCase,
                postCommentUseCase = container.postCommentUseCase,
                getRecommendedVideosUseCase = container.getRecommendedVideosUseCase,
                recommendRepository = container.recommendRepository,
                styleRepository = container.styleRepository
            )
        }
    }
}

/**
 * 扩展函数：便捷获取 Application 实例.
 */
fun CreationExtras.bilidownloaderApplication(): MyApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MyApplication)