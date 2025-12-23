package com.example.bilidownloader.features.aicomment

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
import com.example.bilidownloader.core.model.AiModelConfig

/**
 * AI 评论助手主屏幕.
 *
 * 集成了三大核心功能块：
 * 1. **自动化控制台**：配置模型、查看实时日志、启动/停止自动评论循环。
 * 2. **推荐流过滤**：展示 B 站推荐视频，并支持点击解析。
 * 3. **手动操作区**：支持手动输入 URL 解析、选择评论风格、生成并发送评论。
 */
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

    // 控制新建风格弹窗的显示状态
    var showAddStyleDialog by remember { mutableStateOf(false) }

    // UI 锁定逻辑：当正在执行耗时任务或自动化运行时，禁用部分交互
    val isLocked = state.loadingState != AiCommentLoadingState.Idle || state.isAutoRunning

    // 监听 ViewModel 发出的瞬态事件 (Toast)
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

    if (showAddStyleDialog) {
        AddStyleDialog(
            onDismiss = { showAddStyleDialog = false },
            onConfirm = { label, prompt ->
                viewModel.addCustomStyle(label, prompt)
                showAddStyleDialog = false
            }
        )
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
            // region 1. Model Selector (模型选择)
            ModelSelector(
                currentModel = state.currentModel,
                onModelSelected = { viewModel.updateModel(it) },
                enabled = !state.isAutoRunning
            )
            // endregion

            // region 2. Automation Console (自动化控制台)
            AutomationControlCard(
                isAutoRunning = state.isAutoRunning,
                currentStyle = state.selectedStyle,
                logs = state.autoLogs,
                availableStyles = state.availableStyles,
                onStart = { style -> viewModel.toggleAutomation(style) },
                onStop = { viewModel.toggleAutomation(state.selectedStyle) }
            )
            // endregion

            // region 3. Recommendation Feed (推荐流)
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
                        Text(text = "📺 首页推荐 (智能过滤)", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        IconButton(onClick = { viewModel.fetchRecommendations() }, enabled = !isLocked) {
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
                                            modifier = Modifier.fillMaxWidth().height(80.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Text(
                                            text = "CC字幕",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = Color.White,
                                            modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(topStart = 4.dp)).padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(text = candidate.info.title, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            }
            // endregion

            // region 4. Manual Operation (手动操作区)
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
                    IconButton(onClick = { focusManager.clearFocus(); viewModel.analyzeVideo(urlInput) }, enabled = !isLocked) {
                        Icon(Icons.Default.AutoAwesome, "解析")
                    }
                }
            )

            // 视频信息展示卡片
            if (state.videoTitle.isNotEmpty()) {
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(model = state.videoCover, contentDescription = "Cover", modifier = Modifier.size(80.dp).padding(end = 12.dp), contentScale = ContentScale.Crop)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = state.videoTitle, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                            Spacer(modifier = Modifier.height(4.dp))
                            when {
                                state.isAutoRunning -> Text("🚀 自动化接管中...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                state.loadingState == AiCommentLoadingState.AnalyzingVideo -> Text("正在解析视频...", style = MaterialTheme.typography.bodySmall)
                                state.loadingState == AiCommentLoadingState.FetchingSubtitle -> Text("正在获取字幕...", style = MaterialTheme.typography.bodySmall)
                                else -> {
                                    if (state.isSubtitleReady) Text("✅ 字幕已就绪", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                    else Text("❌ 未获取到字幕", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }

            // 风格选择 Chip Group
            if (state.isSubtitleReady || !state.isAutoRunning) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("选择评论风格", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.weight(1f))
                    if (!state.isAutoRunning) {
                        TextButton(onClick = { showAddStyleDialog = true }) {
                            Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("新建风格")
                        }
                    }
                }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.availableStyles.forEach { style ->
                        FilterChip(
                            selected = state.selectedStyle == style && state.loadingState != AiCommentLoadingState.GeneratingComment,
                            onClick = { viewModel.generateComment(style) },
                            label = { Text(style.label) },
                            enabled = !isLocked,
                            leadingIcon = {
                                if (state.selectedStyle == style && state.loadingState == AiCommentLoadingState.GeneratingComment) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                }
                            },
                            trailingIcon = {
                                // 仅允许删除非内置风格
                                if (!style.isBuiltIn && !isLocked) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "删除",
                                        modifier = Modifier.size(16.dp).clickable { viewModel.deleteCustomStyle(style) }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // 生成内容编辑与发送区
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
            // endregion
        }
    }
}

/**
 * 新建自定义风格弹窗.
 */
@Composable
fun AddStyleDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String) -> Unit
) {
    var label by remember { mutableStateOf("") }
    var prompt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建自定义风格") },
        text = {
            Column {
                OutlinedTextField(
                    value = label,
                    onValueChange = { if (it.length <= 6) label = it },
                    label = { Text("标签名称 (最多6字)") },
                    placeholder = { Text("如：高冷、鲁迅风") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("提示词指令 (Prompt)") },
                    placeholder = { Text("例如：请以一个高冷毒舌的评委口吻，对视频内容进行简短点评...") },
                    modifier = Modifier.fillMaxWidth().height(120.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(label, prompt) },
                enabled = label.isNotBlank() && prompt.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 模型下拉选择器.
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
            modifier = Modifier.fillMaxWidth().menuAnchor(),
            enabled = enabled
        )

        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(model.name, style = MaterialTheme.typography.bodyLarge)
                            Text(if (model.isSmartMode) "自动选择最省钱/最高效的模型" else "厂商: ${model.provider.label}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
                        }
                    },
                    onClick = { onModelSelected(model); expanded = false },
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * 自动化控制台卡片.
 * 显示运行状态、实时日志流以及可选的启动风格。
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AutomationControlCard(
    isAutoRunning: Boolean,
    currentStyle: CommentStyle?,
    logs: List<String>,
    availableStyles: List<CommentStyle>,
    onStart: (CommentStyle) -> Unit,
    onStop: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (isAutoRunning) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = if (isAutoRunning) Icons.Default.PauseCircle else Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(28.dp), tint = if (isAutoRunning) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = if (isAutoRunning) "自动化运行中 (${currentStyle?.label ?: "未知"})" else "全自动驾驶模式", style = MaterialTheme.typography.titleMedium)
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (isAutoRunning) {
                Text(text = "正在自动拉取首页推荐 -> 过滤 -> 总结 -> 评论", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onStop, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Stop, null); Spacer(modifier = Modifier.width(8.dp)); Text("停止任务")
                }
                Spacer(modifier = Modifier.height(16.dp))
                LogConsole(logs = logs)
            } else {
                Text("选择风格启动自动化：", style = MaterialTheme.typography.labelMedium)
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    availableStyles.forEach { style ->
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

/**
 * 日志输出控制台组件.
 * 自动滚动到最新一条日志。
 */
@Composable
fun LogConsole(logs: List<String>) {
    val listState = rememberLazyListState()

    // 监听日志数量变化，自动滚动到底部
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().height(180.dp)) {
        LazyColumn(state = listState, contentPadding = PaddingValues(8.dp), modifier = Modifier.fillMaxSize()) {
            items(logs) { log ->
                Text(text = log, color = Color(0xFF00FF00), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 14.sp)
            }
        }
    }
}