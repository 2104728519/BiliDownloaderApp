package com.example.bilidownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.example.bilidownloader.ui.MainScreen
import com.example.bilidownloader.ui.theme.BiliDownloaderTheme // 确保导入了这个

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 1. 【关键】套上自动生成的主题包
            // 这个名字通常是 "你的项目名 + Theme"
            BiliDownloaderTheme {

                // 2. 【关键】铺一层背景板
                // 它的颜色会跟随系统：白天是白色，晚上是黑色（或深灰色）
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // 3. 显示我们的主界面
                    MainScreen()
                }
            }
        }
    }
}