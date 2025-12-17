package com.example.bilidownloader.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.bilidownloader.MyApplication
import com.example.bilidownloader.ui.viewmodel.AudioPickerViewModel
import com.example.bilidownloader.ui.viewmodel.MainViewModel

/**
 * ViewModel 工厂
 * 负责把 Repository 和 UseCase 注入到 ViewModel 中
 */
object AppViewModelProvider {
    val Factory = viewModelFactory {

        // 1. 配置 MainViewModel 的创建方式
        initializer {
            val container = bilidownloaderApplication().container
            MainViewModel(
                application = bilidownloaderApplication(),
                historyRepository = container.historyRepository,
                userRepository = container.userRepository,
                analyzeVideoUseCase = container.analyzeVideoUseCase,
                downloadVideoUseCase = container.downloadVideoUseCase,
                // 【新增】注入转写准备用例
                prepareTranscribeUseCase = container.prepareTranscribeUseCase
            )
        }

        // 2. 配置 AudioPickerViewModel
        initializer {
            AudioPickerViewModel(
                application = bilidownloaderApplication(),
            )
        }
    }
}

/**
 * 扩展函数：方便从 CreationExtras 中获取 MyApplication 实例
 */
fun CreationExtras.bilidownloaderApplication(): MyApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MyApplication)