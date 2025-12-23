package com.example.bilidownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.bilidownloader.ui.screen.MainScreen
import com.example.bilidownloader.ui.theme.BiliDownloaderTheme

/**
 * 应用程序主入口 Activity.
 * 负责初始化 Compose UI 环境，加载全局主题并承载主屏幕内容.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 应用 Material 3 设计主题
            BiliDownloaderTheme {
                // Surface 作为顶级容器，处理背景色适配（亮色/暗色模式）
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}