package com.example.bilidownloader.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.bilidownloader.MyApplication
import com.example.bilidownloader.features.login.LoginViewModel // 新引用
import com.example.bilidownloader.ui.viewmodel.AiCommentViewModel
import com.example.bilidownloader.ui.viewmodel.AudioPickerViewModel
import com.example.bilidownloader.ui.viewmodel.MainViewModel

object AppViewModelProvider {
    val Factory = viewModelFactory {

        // MainViewModel 注入配置
        initializer {
            val container = bilidownloaderApplication().container
            MainViewModel(
                application = bilidownloaderApplication(),
                historyRepository = container.historyRepository,
                authRepository = container.authRepository, // 更新引用
                analyzeVideoUseCase = container.analyzeVideoUseCase,
                downloadVideoUseCase = container.downloadVideoUseCase,
                prepareTranscribeUseCase = container.prepareTranscribeUseCase,
                getSubtitleUseCase = container.getSubtitleUseCase
            )
        }

        // LoginViewModel 注入配置
        initializer {
            val container = bilidownloaderApplication().container
            LoginViewModel(
                application = bilidownloaderApplication(),
                authRepository = container.authRepository // 注入仓库
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

fun CreationExtras.bilidownloaderApplication(): MyApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MyApplication)