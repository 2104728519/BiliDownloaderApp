package com.example.bilidownloader.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bilidownloader.ui.viewmodel.TranscriptionViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TranscriptionScreen(
    filePath: String, // 【修改】接收路径参数
    onBack: () -> Unit,
    viewModel: TranscriptionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // 1. 页面进入时，自动开始转写
    // 只执行一次 (Unit)
    LaunchedEffect(Unit) {
        viewModel.startTranscription(filePath)
    }

    // 2. 这里的变量用来保存“可编辑”的文本
    var editableText by remember { mutableStateOf("") }

    // 监听状态，如果是成功状态，就把结果填入编辑框
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
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            when (val state = uiState) {
                // 刚进来或者处理中
                is TranscriptionViewModel.TransState.Idle,
                is TranscriptionViewModel.TransState.Processing -> {
                    Spacer(modifier = Modifier.height(100.dp))
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    // 如果是 Idle 可能是刚进来还没切状态，显示个默认文案
                    val text = if(state is TranscriptionViewModel.TransState.Processing) state.step else "准备中..."
                    Text(text)
                }

                // 出错
                is TranscriptionViewModel.TransState.Error -> {
                    Spacer(modifier = Modifier.height(100.dp))
                    Text(state.msg, color = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { viewModel.startTranscription(filePath) }) { // 重试
                        Text("重试")
                    }
                }

                // 成功：显示编辑框
                is TranscriptionViewModel.TransState.Success -> {
                    Text("转写结果 (可编辑)：", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
                    Spacer(modifier = Modifier.height(8.dp))

                    // 【修改】换成可编辑的输入框
                    OutlinedTextField(
                        value = editableText,
                        onValueChange = { editableText = it }, // 用户修改时更新变量
                        modifier = Modifier
                            .weight(1f) // 占满中间区域
                            .fillMaxWidth(),
                        textStyle = MaterialTheme.typography.bodyLarge
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 复制按钮 (复制的是用户修改后的 editableText)
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("Transcription", editableText)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.ContentCopy, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("复制文本")
                    }
                }
            }
        }
    }
}