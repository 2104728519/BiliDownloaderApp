package com.example.bilidownloader.ui

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext // 【使用】
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.bilidownloader.ui.screen.HomeScreen
import com.example.bilidownloader.ui.screen.ToolsScreen
import com.example.bilidownloader.ui.screen.AudioPickerScreen
import com.example.bilidownloader.ui.screen.AudioCropScreen
import com.example.bilidownloader.utils.StorageHelper // 【导入】
import java.io.File // 【导入】
import java.net.URLEncoder // 编码器不用显式导入，但需要使用
import android.widget.Toast // 确保导入，用于错误提示

@Composable
fun MainScreen() {
    // 1. 获取导航控制器 (这是管家手里的地图)
    val navController = rememberNavController()

    // 2. 搭建页面框架
    Scaffold(
        // 底部导航栏
        bottomBar = {
            // ... (NavigationBar 代码不变)
            NavigationBar {
                // 获取当前我们在哪个房间，用来高亮对应的按钮
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                // === 按钮 1：首页 ===
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

                // === 按钮 2：工具箱 ===
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
        // 3. 导航主机 (这里是真正换页面的地方)
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            // 定义房间 1：首页
            composable("home") {
                HomeScreen()
            }

            // 定义房间 2：工具箱
            composable("tools") {
                ToolsScreen(
                    onNavigateToAudioCrop = {
                        navController.navigate("audio_picker")
                    }
                )
            }

            // 定义页面 3：音频选择页
            composable("audio_picker") {
                val context = LocalContext.current // 【获取 Context】

                AudioPickerScreen(
                    onAudioSelected = { uri ->
                        // 【修改核心逻辑】
                        // 1. 生成一个独一无二的临时文件名
                        val tempName = "temp_crop_${System.currentTimeMillis()}.mp3"

                        // 2. 调用 StorageHelper 复制文件
                        val cacheFile = StorageHelper.copyUriToCache(context, uri, tempName)

                        if (cacheFile != null) {
                            // 3. 复制成功，将本地路径编码后跳转
                            val encodedPath = java.net.URLEncoder.encode(cacheFile.absolutePath, "UTF-8")
                            navController.navigate("audio_crop/$encodedPath")
                        } else {
                            // 复制失败了，弹个提示
                            Toast.makeText(context, "文件读取失败，请重试", Toast.LENGTH_LONG).show()
                        }
                    }
                )
            }

            // 定义页面 4：音频裁剪页
            // 路由格式：audio_crop/{uri}
            composable(
                route = "audio_crop/{uri}",
                arguments = listOf(navArgument("uri") { type = NavType.StringType })
            ) { backStackEntry ->
                // 取出参数 (这里取出的现在是本地文件路径的编码字符串)
                val uriString = backStackEntry.arguments?.getString("uri") ?: ""

                AudioCropScreen(
                    audioUri = uriString, // 注意：现在 audioUri 实际上是本地路径
                    onBack = { navController.popBackStack() } // 处理返回按钮
                )
            }
        }
    }
}