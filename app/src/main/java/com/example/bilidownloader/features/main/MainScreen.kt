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
import com.example.bilidownloader.features.home.HomeScreen
import com.example.bilidownloader.features.login.LoginScreen
import com.example.bilidownloader.features.tools.ToolsScreen
import com.example.bilidownloader.features.tools.audiocrop.AudioCropScreen
import com.example.bilidownloader.features.tools.audiocrop.AudioPickerScreen
import com.example.bilidownloader.features.tools.transcription.TranscriptionScreen
import java.net.URLEncoder

/**
 * 全局主容器 (Scaffold).
 *
 * 包含底部导航栏 (Bottom Navigation) 和路由控制器 (NavHost)。
 * 定义了整个应用的所有页面路由规则和参数传递方式。
 */
@Composable
fun MainScreen() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // 首页 Tab
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

                // 工具 Tab
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Build, contentDescription = null) },
                    label = { Text("工具") },
                    selected = currentRoute == "tools",
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
            // region Home Navigation

            composable("home") {
                HomeScreen(
                    onNavigateToTranscribe = { path ->
                        // 路径包含特殊字符，需 URL 编码
                        val encodedPath = URLEncoder.encode(path, "UTF-8")
                        navController.navigate("transcription/$encodedPath")
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

            // endregion

            // region Tools Navigation

            composable("tools") {
                ToolsScreen(
                    onNavigateToAudioCrop = { navController.navigate("audio_picker") },
                    onNavigateToTranscription = { navController.navigate("audio_picker_transcribe") },
                    onNavigateToAiComment = { navController.navigate("ai_comment") }
                )
            }

            composable("ai_comment") {
                AiCommentScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // 音频选择 (剪辑用)
            composable("audio_picker") {
                val context = LocalContext.current
                AudioPickerScreen(
                    onBack = { navController.popBackStack() },
                    onAudioSelected = { uri ->
                        // 复制到缓存目录，确保文件有绝对路径
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

            // 音频选择 (转写用)
            composable("audio_picker_transcribe") {
                val context = LocalContext.current
                AudioPickerScreen(
                    onBack = { navController.popBackStack() },
                    onAudioSelected = { uri ->
                        val tempName = "trans_input_${System.currentTimeMillis()}.mp3"
                        val cacheFile = StorageHelper.copyUriToCache(context, uri, tempName)

                        if (cacheFile != null) {
                            val encodedPath = URLEncoder.encode(cacheFile.absolutePath, "UTF-8")
                            navController.navigate("transcription/$encodedPath")
                        } else {
                            Toast.makeText(context, "文件读取失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            composable(
                route = "transcription/{path}",
                arguments = listOf(navArgument("path") { type = NavType.StringType })
            ) { backStackEntry ->
                val path = backStackEntry.arguments?.getString("path") ?: ""
                TranscriptionScreen(
                    filePath = path,
                    onBack = { navController.popBackStack() }
                )
            }

            // endregion
        }
    }
}