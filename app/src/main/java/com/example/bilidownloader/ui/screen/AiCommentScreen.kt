package com.example.bilidownloader.ui.screen

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.bilidownloader.di.AppViewModelProvider
import com.example.bilidownloader.domain.model.AiModelConfig // 新增
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

    // UI 锁定状态：正在加载 或 正在自动化运行时，锁定大部分手动操作
    val isLocked = state.loadingState != AiCommentLoadingState.Idle || state.isAutoRunning

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
            // ===========================
            // 0. [新增] 模型选择器
            // ===========================
            ModelSelector(
                currentModel = state.currentModel,
                onModelSelected = { viewModel.updateModel(it) },
                enabled = !state.isAutoRunning // 自动化运行时锁定模型
            )

            // ===========================
            // 1. 自动化控制台
            // ===========================
            AutomationControlCard(
                isAutoRunning = state.isAutoRunning,
                currentStyle = state.selectedStyle,
                logs = state.autoLogs,
                onStart = { style -> viewModel.toggleAutomation(style) },
                onStop = { viewModel.toggleAutomation(state.selectedStyle) }
            )

            // ===========================
            // 2. 首页推荐卡片
            // ===========================
            ElevatedCard(
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = if(isLocked && !state.isAutoRunning) 0.6f else 1f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📺 首页推荐 (智能过滤)",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(
                            onClick = { viewModel.fetchRecommendations() },
                            enabled = !isLocked
                        ) {
                            if (state.loadingState == AiCommentLoadingState.FetchingRecommendations) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Refresh, "刷新推荐")
                            }
                        }
                    }

                    if (state.recommendationList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            items(state.recommendationList) { candidate ->
                                Column(
                                    modifier = Modifier
                                        .width(140.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable(enabled = !isLocked) {
                                            viewModel.applyCandidate(candidate)
                                            urlInput = candidate.info.bvid
                                        }
                                        .then(if (isLocked) Modifier.background(Color.Gray.copy(alpha = 0.1f)) else Modifier)
                                ) {
                                    Box {
                                        AsyncImage(
                                            model = candidate.info.pic,
                                            contentDescription = null,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(80.dp)
                                                .clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Text(
                                            text = "CC字幕",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 4.dp))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = candidate.info.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else if (!state.isAutoRunning) {
                        Text("点击刷新按钮获取 B 站首页推荐", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }

            // ===========================
            // 3. 手动操作区
            // ===========================
            OutlinedTextField(
                value = urlInput,
                onValueChange = { urlInput = it },
                label = { Text("输入视频链接 / BV号") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                enabled = !isLocked,
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
                        enabled = !isLocked
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

                            when {
                                state.isAutoRunning -> Text("🚀 自动化接管中...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                state.loadingState == AiCommentLoadingState.AnalyzingVideo -> Text("正在解析视频...", style = MaterialTheme.typography.bodySmall)
                                state.loadingState == AiCommentLoadingState.FetchingSubtitle -> Text("正在获取字幕...", style = MaterialTheme.typography.bodySmall)
                                else -> {
                                    if (state.isSubtitleReady) {
                                        Text("✅ 字幕已就绪", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    } else {
                                        Text("❌ 未获取到字幕", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (state.isSubtitleReady && !state.isAutoRunning) {
                Text("手动选择评论风格", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CommentStyle.entries.forEach { style ->
                        FilterChip(
                            selected = state.selectedStyle == style && state.loadingState != AiCommentLoadingState.GeneratingComment,
                            onClick = { viewModel.generateComment(style) },
                            label = { Text(style.label) },
                            enabled = !isLocked,
                            leadingIcon = {
                                if (state.selectedStyle == style && state.loadingState == AiCommentLoadingState.GeneratingComment) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                            }
                        )
                    }
                }
            }

            if ((state.generatedContent.isNotEmpty() || state.selectedStyle != null) && !state.isAutoRunning) {
                OutlinedTextField(
                    value = state.generatedContent,
                    onValueChange = { viewModel.updateContent(it) },
                    label = { Text("AI 生成内容 (可编辑)") },
                    modifier = Modifier.fillMaxWidth().height(150.dp),
                    enabled = !isLocked
                )
                Button(
                    onClick = { viewModel.sendComment() },
                    modifier = Modifier.align(Alignment.End),
                    enabled = !isLocked && state.generatedContent.isNotBlank()
                ) {
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

/**
 * [新增] 模型选择器组件
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModelSelector(
    currentModel: AiModelConfig,
    onModelSelected: (AiModelConfig) -> Unit,
    enabled: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val models = remember { AiModelConfig.getAllModels() }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = !expanded },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = "${currentModel.name} [${currentModel.provider.label}]",
            onValueChange = {},
            readOnly = true,
            label = { Text("AI 模型选择") },
            leadingIcon = { Icon(Icons.Default.SmartToy, null) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
            enabled = enabled
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (model.isSmartMode) "自动选择最省钱/最高效的模型" else "厂商: ${model.provider.label}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    },
                    onClick = {
                        onModelSelected(model)
                        expanded = false
                    },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * 独立的自动化控制卡片组件
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AutomationControlCard(
    isAutoRunning: Boolean,
    currentStyle: CommentStyle?,
    logs: List<String>,
    onStart: (CommentStyle) -> Unit,
    onStop: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isAutoRunning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (isAutoRunning) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = if (isAutoRunning) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isAutoRunning) "自动化运行中 (${currentStyle?.label ?: "未知"})" else "全自动驾驶模式",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isAutoRunning) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isAutoRunning) {
                Text(
                    text = "正在自动拉取首页推荐 -> 过滤 -> 总结 -> 评论",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onStop,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Stop, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("停止任务")
                }

                Spacer(modifier = Modifier.height(16.dp))
                LogConsole(logs = logs)
            } else {
                Text("请选择一种风格以启动自动化循环：", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CommentStyle.entries.forEach { style ->
                        SuggestionChip(
                            onClick = { onStart(style) },
                            label = { Text(style.label) },
                            icon = { Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LogConsole(logs: List<String>) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().height(180.dp)
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(8.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(logs) { log ->
                Text(
                    text = log,
                    color = Color(0xFF00FF00),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 14.sp
                )
            }
        }
    }
}