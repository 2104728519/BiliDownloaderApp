package com.example.bilidownloader

import android.app.Application
import com.example.bilidownloader.core.network.NetworkModule
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        NetworkModule.initialize(this)
    }
}