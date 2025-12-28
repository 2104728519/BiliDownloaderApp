package com.example.bilidownloader.features.tools

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 工具箱导航页面.
 * 简单的卡片式菜单，用于进入各个独立功能模块。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onNavigateToAudioCrop: () -> Unit,
    onNavigateToTranscription: () -> Unit,
    onNavigateToAiComment: () -> Unit,
    onNavigateToFfmpeg: () -> Unit // [新增] FFmpeg 导航回调
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("工具箱") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    titleContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 1. 音频裁剪
            ToolCard(
                title = "音频裁剪",
                description = "裁剪本地音频文件，支持导出 MP3",
                icon = Icons.Default.Build,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onNavigateToAudioCrop
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 2. 音频转写
            ToolCard(
                title = "音频转文字",
                description = "调用阿里云 AI 模型进行语音识别",
                icon = Icons.Default.Description,
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                onClick = onNavigateToTranscription
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 3. AI 评论助手
            ToolCard(
                title = "AI 评论助手",
                description = "基于视频字幕生成多种风格的评论",
                icon = Icons.Default.AutoAwesome,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = onNavigateToAiComment
            )

            Spacer(modifier = Modifier.height(16.dp))

            // [新增] 4. FFmpeg 万能终端 (极客风深色卡片)
            ElevatedCard(
                onClick = onNavigateToFfmpeg,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp),
                colors = CardDefaults.elevatedCardColors(
                    containerColor = Color(0xFF1E1E1E) // 深色背景
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = "FFmpeg Terminal",
                        modifier = Modifier.size(40.dp),
                        tint = Color(0xFF00FF00) // 荧光绿图标
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            text = "FFmpeg 终端",
                            style = MaterialTheme.typography.titleMedium,
                            color = Color(0xFF00FF00)
                        )
                        Text(
                            text = "执行自定义 FFmpeg 命令 (Raw Mode)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }
    }
}

/**
 * 通用工具卡片组件，提取公共样式以减少代码冗余
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolCard(
    title: String,
    description: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    onClick: () -> Unit
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = contentColor
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}