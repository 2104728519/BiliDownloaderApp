package com.example.bilidownloader // 请替换成您自己的包名

import android.app.Application
import com.example.bilidownloader.data.api.RetrofitClient

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // 初始化网络客户端
        RetrofitClient.initialize(this)
    }
}