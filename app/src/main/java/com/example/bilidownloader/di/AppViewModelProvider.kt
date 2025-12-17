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
 * 负责把 Repository 注入到 ViewModel 中
 */
object AppViewModelProvider {
    val Factory = viewModelFactory {

        // 1. 配置 MainViewModel 的创建方式
        initializer {
            MainViewModel(
                application =  bilidownloaderApplication(), // 传入 Application Context
                historyRepository = bilidownloaderApplication().container.historyRepository,
                userRepository = bilidownloaderApplication().container.userRepository,
                downloadRepository = bilidownloaderApplication().container.downloadRepository
            )
        }

        // 2. 配置 AudioPickerViewModel (如果需要注入 Repository)
        initializer {
            AudioPickerViewModel(
                application = bilidownloaderApplication(),
                // 注意：AudioPickerViewModel 目前内部 new 了 Repository，暂时保持原样，
                // 后续你可以像 MainViewModel 一样改造它。
            )
        }

        // LoginViewModel 和 TranscriptionViewModel 暂时还比较简单，
        // 可以暂不使用工厂，或者后续再加。
    }
}

/**
 * 扩展函数：方便获取 Application 对象
 */
fun CreationExtras.bilidownloaderApplication(): MyApplication =
    (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MyApplication)