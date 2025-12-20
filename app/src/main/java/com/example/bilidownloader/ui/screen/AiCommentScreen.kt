package com.example.bilidownloader.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.bilidownloader.di.AppViewModelProvider
import com.example.bilidownloader.domain.model.CommentStyle
import com.example.bilidownloader.ui.viewmodel.AiCommentViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiCommentScreen(
    onBack: () -> Unit,
    viewModel: AiCommentViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var urlInput by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    // 处理 Toast 消息
    LaunchedEffect(state.error, state.successMessage) {
        state.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearMessages()
        }
        state.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 评论助手") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. 链接输入区
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("输入视频链接 / BV号") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    focusManager.clearFocus()
                    viewModel.analyzeVideo(urlInput)
                }),
                trailingIcon = {
                    IconButton(onClick = {
                        focusManager.clearFocus()
                        viewModel.analyzeVideo(urlInput)
                    }) {
                        Icon(Icons.Default.AutoAwesome, "解析")
                    }
                }
            )

            // 2. 视频信息卡片
            if (state.videoTitle.isNotEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        AsyncImage(
                            model = state.videoCover,
                            contentDescription = "Cover",
                            modifier = Modifier
                                .size(80.dp)
                                .padding(end = 12.dp),
                            contentScale = ContentScale.Crop
                        )
                        Column {
                            Text(
                                text = state.videoTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            if (state.isLoading) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text("正在获取字幕...", style = MaterialTheme.typography.bodySmall)
                            } else if (state.isSubtitleReady) {
                                Text("✅ 字幕已就绪", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            } else {
                                Text("❌ 未获取到字幕", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }

            // 3. 风格选择区 (仅当字幕就绪时显示)
            if (state.isSubtitleReady) {
                Text("选择评论风格", style = MaterialTheme.typography.labelLarge)

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CommentStyle.values().forEach { style ->
                        FilterChip(
                            selected = state.selectedStyle == style,
                            onClick = { viewModel.generateComment(style) },
                            label = { Text(style.label) },
                            enabled = !state.isLoading
                        )
                    }
                }
            }

            // 4. 内容编辑与发送区
            if (state.generatedContent.isNotEmpty() || state.selectedStyle != null) {
                OutlinedTextField(
                    value = state.generatedContent,
                    onValueChange = { viewModel.updateContent(it) },
                    label = { Text("AI 生成内容 (可编辑)") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                    enabled = !state.isLoading
                )

                Button(
                    onClick = { viewModel.sendComment() },
                    modifier = Modifier.align(Alignment.End),
                    enabled = !state.isLoading && state.generatedContent.isNotBlank()
                ) {
                    Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (state.isLoading) "发送中..." else "确认发送")
                }
            }
        }
    }
}