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
 */
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
            composable("home") {
                HomeScreen(
                    onNavigateToTranscribe = { path, title ->
                        val encodedPath = URLEncoder.encode(path, "UTF-8")
                        val encodedTitle =
                            if (title.isNotEmpty()) URLEncoder.encode(title, "UTF-8") else ""
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

            composable("tools") {
                ToolsScreen(
                    onNavigateToAudioCrop = { navController.navigate("audio_picker") },
                    onNavigateToTranscription = { navController.navigate("audio_picker_transcribe") },
                    onNavigateToFfmpeg = { navController.navigate("ffmpeg_terminal") }
                )
            }

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
            composable("audio_picker") {
                val context = LocalContext.current
                AudioPickerScreen(
                    onBack = { navController.popBackStack() },
                    onAudioSelected = { uri ->
                        // 动态获取真实后缀
                        val extension = StorageHelper.getExtensionFromUri(context, uri)
                        val tempName = "temp_crop_${System.currentTimeMillis()}.$extension"
                        
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

            // --- 语音转写流程 ---
            composable("audio_picker_transcribe") {
                val context = LocalContext.current
                AudioPickerScreen(
                    onBack = { navController.popBackStack() },
                    onAudioSelected = { uri ->
                        val extension = StorageHelper.getExtensionFromUri(context, uri)
                        val tempName = "trans_input_${System.currentTimeMillis()}.$extension"
                        val cacheFile = StorageHelper.copyUriToCache(context, uri, tempName)

                        if (cacheFile != null) {
                            val encodedPath = URLEncoder.encode(cacheFile.absolutePath, "UTF-8")
                            navController.navigate("transcription/$encodedPath?title=")
                        } else {
                            Toast.makeText(context, "文件读取失败", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }

            composable(
                route = "transcription/{path}?title={title}",
                arguments = listOf(
                    navArgument("path") { type = NavType.StringType },
                    navArgument("title") {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = ""
                    }
                )
            ) { backStackEntry ->
                val path = backStackEntry.arguments?.getString("path") ?: ""
                val title = backStackEntry.arguments?.getString("title") ?: ""
                TranscriptionScreen(
                    filePath = path,
                    originalTitle = title,
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }
}
