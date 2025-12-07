package com.example.bilidownloader.ui

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
import com.example.bilidownloader.ui.screen.AudioCropScreen
import com.example.bilidownloader.ui.screen.AudioPickerScreen
import com.example.bilidownloader.ui.screen.HomeScreen
import com.example.bilidownloader.ui.screen.ToolsScreen
import com.example.bilidownloader.ui.screen.TranscriptionScreen
import com.example.bilidownloader.utils.StorageHelper
import java.net.URLEncoder

@Composable
fun MainScreen() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
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
            // 定义房间 1：首页
            composable("home") {
                HomeScreen(
                    onNavigateToTranscribe = { path ->
                        // 收到路径，跳转到转写页
                        val encodedPath = java.net.URLEncoder.encode(path, "UTF-8")
                        navController.navigate("transcription/$encodedPath")
                    }
                )
            }

            // 1. 修改 ToolsScreen 的跳转
            composable("tools") {
                ToolsScreen(
                    onNavigateToAudioCrop = { navController.navigate("audio_picker") }, // 原来的裁剪路线
                    onNavigateToTranscription = { navController.navigate("audio_picker_transcribe") } // 【修改】新的转写路线
                )
            }

            // (给裁剪用的) 保持不变
            composable("audio_picker") {
                val context = LocalContext.current
                AudioPickerScreen(
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

            // 2. 【新增】给转写用的音频选择页
            composable("audio_picker_transcribe") {
                val context = androidx.compose.ui.platform.LocalContext.current
                com.example.bilidownloader.ui.screen.AudioPickerScreen(
                    onAudioSelected = { uri ->
                        // 复用之前的逻辑：先复制到缓存，解决权限问题
                        val tempName = "trans_input_${System.currentTimeMillis()}.mp3"
                        val cacheFile = com.example.bilidownloader.utils.StorageHelper.copyUriToCache(context, uri, tempName)

                        if (cacheFile != null) {
                            val encodedPath = URLEncoder.encode(cacheFile.absolutePath, "UTF-8")
                            // 跳转到转写页，带上文件路径
                            navController.navigate("transcription/$encodedPath")
                        } else {
                            android.widget.Toast.makeText(context, "文件读取失败", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            // 3. 【修改】转写页 (接收路径参数)
            composable(
                route = "transcription/{path}",
                arguments = listOf(androidx.navigation.navArgument("path") { type = androidx.navigation.NavType.StringType })
            ) { backStackEntry ->
                val path = backStackEntry.arguments?.getString("path") ?: ""
                com.example.bilidownloader.ui.screen.TranscriptionScreen(
                    filePath = path, // 传参给界面
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}