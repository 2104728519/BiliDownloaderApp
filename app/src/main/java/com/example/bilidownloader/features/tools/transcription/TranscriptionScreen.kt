package com.example.bilidownloader.features.tools.transcription

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.net.URLDecoder

/**
 * 音频转写详情页 (Transcription Screen).
 *
 * 负责展示阿里云“通义听悟”转写的完整流程：
 * 1. **上传**: 将本地音频上传至 OSS（因为 API 仅支持公网 URL）。
 * 2. **处理**: 轮询转写任务状态。
 * 3. **结果**: 展示转写后的文本，支持复制和导出为 TXT。
 *
 * @param filePath 音频文件的本地路径 (通常经过 URL 编码).
 * @param originalTitle 原始视频标题 (可选). 如果提供，导出文件将以此命名；否则使用文件名.
 * @param onBack 返回回调.
 * @param viewModel 业务逻辑持有者.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionScreen(
    filePath: String,
    originalTitle: String = "",
    onBack: () -> Unit,
    viewModel: TranscriptionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 计算显示用的文件名
    val displayFileName = remember(filePath, originalTitle) {
        if (originalTitle.isNotBlank()) {
            originalTitle
        } else {
            try {
                File(URLDecoder.decode(filePath, "UTF-8")).name
            } catch (e: Exception) {
                "未知音频文件"
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("音频转文字") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    ) { padding ->
        val isSuccessState = uiState is TranscriptionViewModel.TransState.Success

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 状态内容区域
            if (isSuccessState) {
                SuccessState(
                    text = (uiState as TranscriptionViewModel.TransState.Success).text,
                    onTextChange = { /* 暂不支持编辑，只读展示 */ },
                    onExport = { content -> viewModel.exportTranscript(content, displayFileName) }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (val state = uiState) {
                        is TranscriptionViewModel.TransState.Idle -> {
                            IdleState(displayFileName) { viewModel.startTranscription(filePath) }
                        }

                        is TranscriptionViewModel.TransState.Processing -> {
                            ProcessingState(state)
                        }

                        is TranscriptionViewModel.TransState.Error -> {
                            ErrorState(state) { viewModel.startTranscription(filePath) }
                        }
                        else -> {}
                    }
                }
            }
        }
    }
}

/**
 * 空闲状态组件.
 * 显示待处理的文件名和开始按钮.
 */
@Composable
private fun IdleState(fileName: String, onStart: () -> Unit) {
    Spacer(modifier = Modifier.height(48.dp))
    Icon(Icons.Default.Audiotrack, "audio", Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(24.dp))
    Text("已选择文件", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    Text(fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(48.dp))
    Button(onClick = onStart,
        Modifier
            .fillMaxWidth()
            .height(50.dp)) {
        Icon(Icons.Default.CloudUpload, null)
        Spacer(Modifier.width(8.dp))
        Text("开始上传并转写")
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        "提示：音频将上传至阿里云进行处理",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
    )
}

/**
 * 处理中状态组件.
 * 显示加载圈和当前步骤信息.
 */
@Composable
private fun ProcessingState(state: TranscriptionViewModel.TransState.Processing) {
    Spacer(modifier = Modifier.height(100.dp))
    CircularProgressIndicator(Modifier.size(48.dp))
    Spacer(Modifier.height(24.dp))
    Text(state.step, style = MaterialTheme.typography.bodyLarge)
}

/**
 * 错误状态组件.
 * 显示错误信息和重试按钮.
 */
@Composable
private fun ErrorState(state: TranscriptionViewModel.TransState.Error, onRetry: () -> Unit) {
    Spacer(modifier = Modifier.height(100.dp))
    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(60.dp))
    Spacer(Modifier.height(16.dp))
    Text(
        "转写失败",
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.error
    )
    Spacer(Modifier.height(8.dp))
    Text(
        state.msg,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
    )
    Spacer(Modifier.height(32.dp))
    Button(onClick = onRetry) {
        Icon(Icons.Default.Refresh, null)
        Spacer(Modifier.width(8.dp))
        Text("重试")
    }
}

/**
 * 成功状态组件.
 * 展示转写结果文本，提供复制和导出功能.
 */
@Composable
private fun ColumnScope.SuccessState(
    text: String,
    onTextChange: (String) -> Unit,
    onExport: (String) -> Unit
) {
    val context = LocalContext.current
    var currentText by remember(text) { mutableStateOf(text) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("转写结果：", style = MaterialTheme.typography.titleMedium)

        Row {
            IconButton(onClick = { onExport(currentText) }) {
                Icon(Icons.Default.SaveAlt, "导出为TXT")
            }

            IconButton(onClick = {
                val clipboard =
                    context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("Transcription", currentText))
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }) {
                Icon(Icons.Default.ContentCopy, "复制")
            }
        }
    }

    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = currentText,
        onValueChange = {
            currentText = it
            onTextChange(it)
        },
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge
    )
}
