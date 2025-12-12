package com.example.bilidownloader.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
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
import com.example.bilidownloader.ui.viewmodel.TranscriptionViewModel
import java.io.File
import java.net.URLDecoder

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionScreen(
    filePath: String,
    onBack: () -> Unit,
    viewModel: TranscriptionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 解析文件名用于展示 (解码 URL)
    val fileName = remember(filePath) {
        try {
            val decodedPath = URLDecoder.decode(filePath, "UTF-8")
            File(decodedPath).name
        } catch (e: Exception) {
            "未知音频文件"
        }
    }

    // 成功后保存文本用于编辑
    var editableText by remember { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is TranscriptionViewModel.TransState.Success) {
            editableText = (uiState as TranscriptionViewModel.TransState.Success).text
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("音频转文字") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            when (val state = uiState) {
                // --- 1. 初始状态：等待用户确认 ---
                is TranscriptionViewModel.TransState.Idle -> {
                    Spacer(modifier = Modifier.height(48.dp))

                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "已选择文件",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = fileName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    Button(
                        onClick = { viewModel.startTranscription(filePath) },
                        modifier = Modifier.fillMaxWidth().height(50.dp)
                    ) {
                        Icon(Icons.Default.CloudUpload, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("开始上传并转写")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "提示：音频将上传至阿里云进行处理",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                }

                // --- 2. 处理中 ---
                is TranscriptionViewModel.TransState.Processing -> {
                    Spacer(modifier = Modifier.height(100.dp))
                    CircularProgressIndicator(modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = state.step,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                // --- 3. 失败 ---
                is TranscriptionViewModel.TransState.Error -> {
                    Spacer(modifier = Modifier.height(100.dp))
                    Icon(
                        Icons.Default.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(60.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "转写失败",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = state.msg,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = { viewModel.startTranscription(filePath) }) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("重试")
                    }
                }

                // --- 4. 成功 ---
                is TranscriptionViewModel.TransState.Success -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("转写结果：", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Transcription", editableText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
                        }) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = editableText,
                        onValueChange = { editableText = it },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
}