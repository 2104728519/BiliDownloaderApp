package com.example.bilidownloader

import android.app.Application
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.di.AppContainer
import com.example.bilidownloader.di.DefaultAppContainer

class MyApplication : Application() {

    // 对外暴露容器
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()

        // 1. 初始化网络模块
        NetworkModule.initialize(this)

        // 2. 初始化依赖容器
        container = DefaultAppContainer(this)
    }
}