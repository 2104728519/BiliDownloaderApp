package com.example.bilidownloader

import android.app.Application
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.di.AppContainer
import com.example.bilidownloader.di.DefaultAppContainer

/**
 * 全局 Application 类.
 * 负责初始化应用级单例，包括网络模块配置和依赖注入容器 (DI Container).
 */
class MyApplication : Application() {

    /** 全局依赖注入容器实例 */
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()

        // 初始化网络配置（传入 Application Context 以处理持久化 Cookie 等）
        NetworkModule.initialize(this)

        // 初始化手动依赖注入容器
        container = DefaultAppContainer(this)
    }
}