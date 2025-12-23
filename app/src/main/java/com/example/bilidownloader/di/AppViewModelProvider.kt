package com.example.bilidownloader.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.bilidownloader.MyApplication
import com.example.bilidownloader.features.aicomment.AiCommentViewModel // 新引用
import com.example.bilidownloader.features.login.LoginViewModel
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
                authRepository = container.authRepository,
                analyzeVideoUseCase = container.analyzeVideoUseCase,
                downloadVideoUseCase = container.downloadVideoUseCase,
                prepareTranscribeUseCase = container.prepareTranscribeUseCase,
                subtitleRepository = container.subtitleRepository // 替换 UseCase
            )
        }

        // LoginViewModel 注入配置
        initializer {
            val container = bilidownloaderApplication().container
            LoginViewModel(
                application = bilidownloaderApplication(),
                authRepository = container.authRepository
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
                subtitleRepository = container.subtitleRepository, // 注入 Repo
                llmRepository = container.llmRepository,           // 注入 Repo
                commentRepository = container.commentRepository,   // 注入 Repo
                recommendRepository = container.recommendRepository, // 注入 Repo
                styleRepository = container.styleRepository        // 注入 Repo
            )
        }
    }
}

fun CreationExtras.bilidownloaderApplication(): MyApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MyApplication)