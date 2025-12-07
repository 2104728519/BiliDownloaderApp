package com.example.bilidownloader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.bilidownloader.ui.MainScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // 【修改】这里不再直接显示 HomeScreen，而是显示带导航栏的 MainScreen
            MainScreen()
        }
    }
}