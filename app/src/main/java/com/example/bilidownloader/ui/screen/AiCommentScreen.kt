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
import com.example.bilidownloader.ui.viewmodel.AiCommentLoadingState
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
    val isLoading = state.loadingState != AiCommentLoadingState.Idle

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
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") } }
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
                    IconButton(
                        onClick = {
                            focusManager.clearFocus()
                            viewModel.analyzeVideo(urlInput)
                        },
                        enabled = !isLoading // 任何加载时都禁用
                    ) {
                        Icon(Icons.Default.AutoAwesome, "解析")
                    }
                }
            )

            if (state.videoTitle.isNotEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(
                            model = state.videoCover,
                            contentDescription = "Cover",
                            modifier = Modifier.size(80.dp).padding(end = 12.dp),
                            contentScale = ContentScale.Crop
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = state.videoTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Spacer(modifier = Modifier.height(4.dp))

                            // [关键修改 1] 使用 when 语句显示精确的状态
                            when (state.loadingState) {
                                AiCommentLoadingState.AnalyzingVideo -> {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text("正在解析视频...", style = MaterialTheme.typography.bodySmall)
                                }
                                AiCommentLoadingState.FetchingSubtitle -> {
                                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    Text("正在获取字幕...", style = MaterialTheme.typography.bodySmall)
                                }
                                else -> {
                                    if (state.isSubtitleReady) {
                                        Text("✅ 字幕已就绪", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Text("❌ 未获取到字幕或解析失败", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.isSubtitleReady) {
                Text("选择评论风格", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CommentStyle.values().forEach { style ->
                        FilterChip(
                            selected = state.selectedStyle == style && state.loadingState != AiCommentLoadingState.GeneratingComment,
                            onClick = { viewModel.generateComment(style) },
                            label = { Text(style.label) },
                            enabled = !isLoading, // 任何加载时都禁用
                            leadingIcon = {
                                if (state.selectedStyle == style && state.loadingState == AiCommentLoadingState.GeneratingComment) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                            }
                        )
                    }
                }
            }

            if (state.generatedContent.isNotEmpty() || state.selectedStyle != null) {
                OutlinedTextField(
                    value = state.generatedContent,
                    onValueChange = { viewModel.updateContent(it) },
                    label = { Text("AI 生成内容 (可编辑)") },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    enabled = !isLoading
                )
                Button(
                    onClick = { viewModel.sendComment() },
                    modifier = Modifier.align(Alignment.End),
                    enabled = !isLoading && state.generatedContent.isNotBlank()
                ) {
                    // [关键修改 2] 只在“发送”时显示加载状态
                    if (state.loadingState == AiCommentLoadingState.SendingComment) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("发送中...")
                    } else {
                        Icon(Icons.Default.Send, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("确认发送")
                    }
                }
            }
        }
    }
}