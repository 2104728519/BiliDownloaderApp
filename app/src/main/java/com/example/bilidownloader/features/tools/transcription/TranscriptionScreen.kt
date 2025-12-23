package com.example.bilidownloader.features.tools.transcription

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bilidownloader.core.manager.CookieManager
import java.io.File
import java.net.URLDecoder

/**
 * 音频转写详情页.
 *
 * 流程：
 * 1. 展示文件名和用量统计 (UsageCard)。
 * 2. 用户点击“开始” -> 上传文件到 OSS -> 提交转写任务。
 * 3. 轮询任务状态 (Processing)。
 * 4. 成功后显示结果文本框 (Success)，支持复制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionScreen(
    filePath: String,
    onBack: () -> Unit,
    viewModel: TranscriptionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val usageState by viewModel.usageState.collectAsState()
    val context = LocalContext.current

    val fileName = remember(filePath) {
        try {
            File(URLDecoder.decode(filePath, "UTF-8")).name
        } catch (e: Exception) { "未知音频文件" }
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
            // 用量统计卡片
            UsageCard(usageState, onRefresh = { viewModel.loadUsage() })
            Spacer(Modifier.height(16.dp))

            // 成功状态需要填满屏幕以显示大量文本，其他状态可滚动
            if (isSuccessState) {
                SuccessState(
                    text = (uiState as TranscriptionViewModel.TransState.Success).text,
                    onTextChange = { /* 只读展示，或者 viewModel 更新 */ }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (val state = uiState) {
                        is TranscriptionViewModel.TransState.Idle -> IdleState(fileName) { viewModel.startTranscription(filePath) }
                        is TranscriptionViewModel.TransState.Processing -> ProcessingState(state)
                        is TranscriptionViewModel.TransState.Error -> ErrorState(state) { viewModel.startTranscription(filePath) }
                        else -> {}
                    }
                }
            }
        }
    }
}

@Composable
private fun UsageCard(
    usageState: TranscriptionViewModel.UsageState,
    onRefresh: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 从 CookieManager 加载上次保存的凭证
    var cookieInput by remember { mutableStateOf(CookieManager.getAliyunConsoleCookie(context) ?: "") }
    var tokenInput by remember { mutableStateOf(CookieManager.getAliyunConsoleSecToken(context) ?: "") }

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(2.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("本月免费额度", style = MaterialTheme.typography.titleMedium)
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = "展开")
            }

            Spacer(Modifier.height(8.dp))

            when (usageState) {
                is TranscriptionViewModel.UsageState.Loading -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                is TranscriptionViewModel.UsageState.Success -> {
                    val progress = (usageState.usedMinutes / usageState.totalMinutes).toFloat()
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "已用: ${"%.2f".format(usageState.usedMinutes)} / ${usageState.totalMinutes.toInt()} 分钟",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.align(Alignment.End)
                    )
                }
                is TranscriptionViewModel.UsageState.Error -> Text(usageState.msg, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                is TranscriptionViewModel.UsageState.Idle -> Text("请先配置凭证以查询用量", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    OutlinedTextField(
                        value = cookieInput,
                        onValueChange = { cookieInput = it },
                        label = { Text("粘贴 Cookie") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 4
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("粘贴 sec_token") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = {
                            if (cookieInput.isNotBlank() && tokenInput.isNotBlank()) {
                                CookieManager.saveAliyunConsoleCredentials(context, cookieInput, tokenInput)
                                Toast.makeText(context, "凭证已保存", Toast.LENGTH_SHORT).show()
                                onRefresh()
                            } else {
                                Toast.makeText(context, "Cookie 和 Token 不能为空", Toast.LENGTH_SHORT).show()
                            }
                        }) { Text("保存并刷新") }
                    }
                }
            }
        }
    }
}

@Composable
private fun IdleState(fileName: String, onStart: () -> Unit) {
    Spacer(modifier = Modifier.height(48.dp))
    Icon(Icons.Default.Audiotrack, "audio", Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
    Spacer(modifier = Modifier.height(24.dp))
    Text("已选择文件", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
    Text(fileName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
    Spacer(modifier = Modifier.height(48.dp))
    Button(onClick = onStart, Modifier.fillMaxWidth().height(50.dp)) {
        Icon(Icons.Default.CloudUpload, null)
        Spacer(Modifier.width(8.dp))
        Text("开始上传并转写")
    }
    Spacer(modifier = Modifier.height(16.dp))
    Text("提示：音频将上传至阿里云进行处理", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
}

@Composable
private fun ProcessingState(state: TranscriptionViewModel.TransState.Processing) {
    Spacer(modifier = Modifier.height(100.dp))
    CircularProgressIndicator(Modifier.size(48.dp))
    Spacer(Modifier.height(24.dp))
    Text(state.step, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun ErrorState(state: TranscriptionViewModel.TransState.Error, onRetry: () -> Unit) {
    Spacer(modifier = Modifier.height(100.dp))
    Icon(Icons.Default.ErrorOutline, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(60.dp))
    Spacer(Modifier.height(16.dp))
    Text("转写失败", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(8.dp))
    Text(state.msg, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
    Spacer(Modifier.height(32.dp))
    Button(onClick = onRetry) {
        Icon(Icons.Default.Refresh, null)
        Spacer(Modifier.width(8.dp))
        Text("重试")
    }
}

@Composable
private fun ColumnScope.SuccessState(text: String, onTextChange: (String) -> Unit) {
    val context = LocalContext.current
    var currentText by remember(text) { mutableStateOf(text) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("转写结果：", style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Transcription", currentText))
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        }) {
            Icon(Icons.Default.ContentCopy, "复制")
        }
    }

    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = currentText,
        onValueChange = {
            currentText = it
            onTextChange(it)
        },
        modifier = Modifier.weight(1f).fillMaxWidth(),
        textStyle = MaterialTheme.typography.bodyLarge
    )
}