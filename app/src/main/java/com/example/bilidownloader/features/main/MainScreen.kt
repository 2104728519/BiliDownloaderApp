package com.example.bilidownloader.features.main

import android.widget.Toast
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.bilidownloader.core.util.StorageHelper
import com.example.bilidownloader.features.aicomment.AiCommentScreen
import com.example.bilidownloader.features.ffmpeg.FfmpegScreen
import com.example.bilidownloader.features.home.HomeScreen
import com.example.bilidownloader.features.login.LoginScreen
import com.example.bilidownloader.features.tools.ToolsScreen
import com.example.bilidownloader.features.tools.audiocrop.AudioCropScreen
import com.example.bilidownloader.features.tools.audiocrop.AudioPickerScreen
import com.example.bilidownloader.features.tools.transcription.TranscriptionScreen
import java.net.URLEncoder

/**
 * 全局主容器 (Scaffold) 与路由配置中心.
 *
 * 职责：
 * 1. 维护底部导航栏 (Bottom Navigation) 的状态与切换.
 * 2. 定义全 App 的路由表 (NavGraph).
 * 3. 处理页面间的参数传递 (特别是路径和标题的编码传输).
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // --- 首页 Tab ---
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Home, contentDescription = null) },
                    label = { Text("首页") },
                    selected = currentRoute == "home",
                    onClick = {
                        navController.navigate("home") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )

                // --- 工具 Tab ---
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    label = { Text("工具") },
                    // 只要是 tools 开头或特定工具页面，都高亮该 Tab
                    selected = currentRoute?.startsWith("tools") == true ||
                            currentRoute?.startsWith("ffmpeg_terminal") == true,
                    onClick = {
                        navController.navigate("tools") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            // ========================================================================
            // Region: 首页模块
            // ========================================================================

            composable("home") {
                HomeScreen(
                    // [关键修改] 接收 path 和 title 两个参数
                    onNavigateToTranscribe = { path, title ->
                        // URL 编码防止特殊字符 (如 / : ?) 破坏路由结构
                        val encodedPath = URLEncoder.encode(path, "UTF-8")
                        val encodedTitle =
                            if (title.isNotEmpty()) URLEncoder.encode(title, "UTF-8") else ""

                        // 路由跳转：带上 title 参数
                        navController.navigate("transcription/$encodedPath?title=$encodedTitle")
                    },
                    onNavigateToLogin = {
                        navController.navigate("login")
                    }
                )
            }

            composable("login") {
                LoginScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // ========================================================================
            // Region: 工具模块
            // ========================================================================

            // 工具箱主页
            composable("tools") {
                ToolsScreen(
                    onNavigateToAudioCrop = { navController.navigate("audio_picker") },
                    onNavigateToTranscription = { navController.navigate("audio_picker_transcribe") },
                    onNavigateToAiComment = { navController.navigate("ai_comment") },
                    onNavigateToFfmpeg = { navController.navigate("ffmpeg_terminal") }
                )
            }

            // AI 评论生成器
            composable("ai_comment") {
                AiCommentScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // FFmpeg 万能终端 (支持预设参数 args)
            composable(
                route = "ffmpeg_terminal?args={args}",
                arguments = listOf(
                    navArgument("args") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) {
                FfmpegScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // --- 音频剪辑流程 ---

            // 1. 选择音频 (剪辑用)
            composable("audio_picker") {
                val context = LocalContext.current
                AudioPickerScreen(
                    onBack = { navController.popBackStack() },
                    onAudioSelected = { uri ->
                        val tempName = "temp_crop_${System.currentTimeMillis()}.mp3"
                        val cacheFile = StorageHelper.copyUriToCache(context, uri, tempName)
                        if (cacheFile != null) {
                            val encodedPath = URLEncoder.encode(cacheFile.absolutePath, "UTF-8")
                            navController.navigate("audio_crop/$encodedPath")
                        } else {
                            Toast.makeText(context, "文件读取失败，请重试", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }

            // 2. 剪辑界面
            composable(
                route = "audio_crop/{uri}",
                arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) { backStackEntry ->
                val uriString = backStackEntry.arguments?.getString("uri") ?: ""
                AudioCropScreen(
                    audioUri = uriString,
                    onBack = { navController.popBackStack() }
                )
            }

            // --- 语音转写流程 ---

            // 1. 选择音频 (转写用)
            composable("audio_picker_transcribe") {
                val context = LocalContext.current
                AudioPickerScreen(
                    onBack = { navController.popBackStack() },
                    onAudioSelected = { uri ->
                        // 创建临时文件
                        val tempName = "trans_input_${System.currentTimeMillis()}.mp3"
                        val cacheFile = StorageHelper.copyUriToCache(context, uri, tempName)

                        if (cacheFile != null) {
                            val encodedPath = URLEncoder.encode(cacheFile.absolutePath, "UTF-8")
                            // 从文件管理器选择的音频没有 B 站标题，title 传空
                            navController.navigate("transcription/$encodedPath?title=")
                        } else {
                            Toast.makeText(context, "文件读取失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 2. 转写界面
            //  路由增加了可选参数 ?title={title}
            composable(
                route = "transcription/{path}?title={title}",
                arguments = listOf(
                    navArgument("path") { type = NavType.StringType },
                    navArgument("title") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = "" // 默认值为空字符串
                    }
                )
            ) { backStackEntry ->
                val path = backStackEntry.arguments?.getString("path") ?: ""
                val title = backStackEntry.arguments?.getString("title") ?: ""

                TranscriptionScreen(
                    filePath = path,
                    originalTitle = title, // 将标题传递给 UI
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}