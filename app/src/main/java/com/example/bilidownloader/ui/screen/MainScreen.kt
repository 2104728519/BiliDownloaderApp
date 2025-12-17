package com.example.bilidownloader.ui.screen

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
import com.example.bilidownloader.ui.screen.*
import com.example.bilidownloader.utils.StorageHelper
import java.net.URLEncoder

@Composable
fun MainScreen() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            // 这里通常可以加逻辑：只有在首页和工具页才显示底部栏，
            // 但为了保持原有结构简单，暂不隐藏
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

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
            // --- 首页 ---
            composable("home") {
                HomeScreen(
                    onNavigateToTranscribe = { path ->
                        // 收到路径，跳转到转写页
                        val encodedPath = URLEncoder.encode(path, "UTF-8")
                        navController.navigate("transcription/$encodedPath")
                    },
                    onNavigateToLogin = {
                        navController.navigate("login")
                    }
                )
            }

            // --- 登录页 ---
            composable("login") {
                LoginScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            // --- 工具箱首页 ---
            composable("tools") {
                ToolsScreen(
                    onNavigateToAudioCrop = { navController.navigate("audio_picker") },
                    onNavigateToTranscription = { navController.navigate("audio_picker_transcribe") }
                )
            }

            // --- 音频选择页 (用于剪辑) ---
            composable("audio_picker") {
                val context = LocalContext.current
                AudioPickerScreen(
                    onBack = { navController.popBackStack() }, // 【新增】返回处理
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

            // --- 剪辑详情页 ---
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

            // --- 音频选择页 (用于转写) ---
            composable("audio_picker_transcribe") {
                val context = LocalContext.current
                AudioPickerScreen(
                    onBack = { navController.popBackStack() }, // 【新增】返回处理
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

            // --- 转写详情页 ---
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
        }
    }
}