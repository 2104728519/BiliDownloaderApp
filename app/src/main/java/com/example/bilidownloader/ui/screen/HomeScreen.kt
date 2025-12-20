package com.example.bilidownloader.ui.screen

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.bilidownloader.data.database.HistoryEntity
import com.example.bilidownloader.data.database.UserEntity
import com.example.bilidownloader.di.AppViewModelProvider
import com.example.bilidownloader.ui.components.BiliWebPlayer
import com.example.bilidownloader.ui.components.HistoryItem
import com.example.bilidownloader.ui.state.FormatOption
import com.example.bilidownloader.ui.state.MainState
import com.example.bilidownloader.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel(factory = AppViewModelProvider.Factory),
    onNavigateToTranscribe: (String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Android 13+ 通知权限
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* 处理权限结果 */ }
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // 状态监听
    val currentUser by viewModel.currentUser.collectAsState()
    val userList by viewModel.userList.collectAsState()
    val state by viewModel.state.collectAsState()
    val historyList by viewModel.historyList.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<HistoryEntity>() }

    // 弹窗控制
    var showAccountDialog by remember { mutableStateOf(false) }
    var showManualCookieInput by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.syncCookieToUserDB()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    BackHandler(enabled = isSelectionMode || state !is MainState.Idle) {
        if (isSelectionMode) exitSelectionMode()
        else if (state !is MainState.Idle) viewModel.reset()
    }

    // --- 弹窗逻辑 ---
    // 账号弹窗省略 (保持之前代码一致)

    if (showSubtitleDialog && state is MainState.ChoiceSelect) {
        SubtitleDialog(
            currentState = state as MainState.ChoiceSelect,
            viewModel = viewModel,
            onDismiss = { showSubtitleDialog = false },
            onNavigateToTranscribe = {
                showSubtitleDialog = false
                onNavigateToTranscribe(it)
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (isSelectionMode) "已选 ${selectedItems.size} 项" else "B 站下载器") },
                navigationIcon = {
                    if (state !is MainState.Idle && !isSelectionMode) {
                        IconButton(onClick = { viewModel.reset() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            viewModel.deleteHistories(selectedItems.toList())
                            exitSelectionMode()
                        }) { Icon(Icons.Default.Delete, contentDescription = "删除") }
                    } else {
                        IconButton(onClick = { showAccountDialog = true }) {
                            if (currentUser != null) {
                                AsyncImage(
                                    model = currentUser?.face,
                                    contentDescription = "Avatar",
                                    modifier = Modifier.size(28.dp).clip(CircleShape)
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = "账号")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (val currentState = state) {
                is MainState.Idle -> {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("粘贴 B 站链接或文字") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3, maxLines = 6,
                        trailingIcon = { if (inputText.isNotEmpty()) IconButton(onClick = { inputText = "" }) { Icon(Icons.Default.Close, contentDescription = null) } }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.analyzeInput(inputText) },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = inputText.isNotBlank()
                    ) { Text("开始解析") }
                    Spacer(modifier = Modifier.height(24.dp))
                    if (historyList.isNotEmpty()) {
                        Text("历史记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(historyList) { history ->
                            HistoryItem(
                                history = history,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedItems.contains(history),
                                onClick = { if (isSelectionMode) { if (selectedItems.contains(history)) selectedItems.remove(history) else selectedItems.add(history) } else viewModel.analyzeInput(history.bvid) },
                                onLongClick = { if (!isSelectionMode) { isSelectionMode = true; selectedItems.add(history) } }
                            )
                        }
                    }
                }

                is MainState.Analyzing -> {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 100.dp))
                }

                is MainState.ChoiceSelect -> {
                    Column(
                        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BiliWebPlayer(bvid = currentState.detail.bvid)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(currentState.detail.title, style = MaterialTheme.typography.titleMedium)
                        Spacer(modifier = Modifier.height(16.dp))

                        QualitySelector("视频画质", currentState.videoFormats, currentState.selectedVideo) { viewModel.updateSelectedVideo(it) }
                        Spacer(modifier = Modifier.height(8.dp))
                        QualitySelector("音频音质", currentState.audioFormats, currentState.selectedAudio) { viewModel.updateSelectedAudio(it) }

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.startDownload(false) }, modifier = Modifier.fillMaxWidth()) { Text("下载 MP4") }
                        OutlinedButton(onClick = { viewModel.startDownload(true) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) { Text("仅下载音频") }

                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = { showSubtitleDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI 智能摘要 / 字幕")
                        }
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }

                is MainState.Processing -> {
                    Column(modifier = Modifier.padding(top = 100.dp).padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(currentState.info, style = MaterialTheme.typography.bodyLarge)
                        LinearProgressIndicator(progress = { currentState.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                        Text("${(currentState.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(32.dp))
                        val isDownloadingPhase = currentState.progress < 0.9f
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (currentState.info.contains("暂停")) Button(onClick = { viewModel.resumeDownload() }) { Text("继续") }
                            else OutlinedButton(onClick = { viewModel.pauseDownload() }, enabled = isDownloadingPhase) { Text("暂停") }
                            TextButton(onClick = { viewModel.cancelDownload() }, enabled = isDownloadingPhase, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text("取消") }
                        }
                    }
                }

                is MainState.Success -> {
                    Column(modifier = Modifier.padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉 ${currentState.message}", color = MaterialTheme.colorScheme.primary)
                        Button(onClick = { viewModel.reset() }, modifier = Modifier.padding(top = 24.dp)) { Text("完成") }
                    }
                }
                is MainState.Error -> {
                    Column(modifier = Modifier.padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("❌ ${currentState.errorMsg}", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.reset() }, modifier = Modifier.padding(top = 24.dp)) { Text("重试") }
                    }
                }
            }
        }
    }
}

// =========================================================
// 【核心修改】独立的字幕弹窗组件
// =========================================================
@Composable
fun SubtitleDialog(
    currentState: MainState.ChoiceSelect,
    viewModel: MainViewModel,
    onDismiss: () -> Unit,
    onNavigateToTranscribe: (String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    if (currentState.subtitleContent.startsWith("ERROR:")) {
        val errorMsg = currentState.subtitleContent.removePrefix("ERROR:")
        LaunchedEffect(errorMsg) {
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
            viewModel.consumeSubtitleError()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.85f).padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                // =========================================================
                // 1. 标题栏 (已应用修改)
                // =========================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 逻辑：如果有数据，显示返回箭头；如果没有，显示默认图标
                        if (currentState.subtitleData != null) {
                            IconButton(onClick = { viewModel.clearSubtitleState() }) {
                                Icon(Icons.Default.ArrowBack, contentDescription = "重选")
                            }
                        } else {
                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        Text(
                            if (currentState.subtitleData != null) "字幕详情" else "AI 摘要 & 字幕",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // 2. 内容区域
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (currentState.isSubtitleLoading) {
                        CircularProgressIndicator()
                    } else if (currentState.subtitleData == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Subtitles, null, Modifier.size(64.dp), MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("获取视频的 AI 总结与字幕", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = { viewModel.fetchSubtitle() }) { Text("立即获取 (B站 API)") }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("或者", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(16.dp))
                            OutlinedButton(onClick = { viewModel.prepareForTranscription { onNavigateToTranscribe(it) } }) {
                                Text("没有字幕？试试阿里云转写")
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = currentState.subtitleContent,
                            onValueChange = { viewModel.updateSubtitleContent(it) },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.5),
                            label = { Text("预览与编辑") }
                        )
                    }
                }

                // 3. 底部工具栏
                if (currentState.subtitleData != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(checked = currentState.isTimestampEnabled, onCheckedChange = { viewModel.toggleTimestamp(it) }, modifier = Modifier.scale(0.8f))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("时间轴", style = MaterialTheme.typography.bodyMedium)
                        }
                        Button(onClick = {
                            clipboardManager.setText(AnnotatedString(currentState.subtitleContent))
                            Toast.makeText(context, "内容已复制", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        }) {
                            Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("复制并关闭")
                        }
                    }
                }
            }
        }
    }
}

// 辅助组件
fun Modifier.scale(scale: Float): Modifier = this.then(Modifier.graphicsLayer(scaleX = scale, scaleY = scale))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountItem(user: UserEntity, isCurrent: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = user.face, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.name, style = MaterialTheme.typography.bodyLarge, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onDelete) { Icon(Icons.Default.Close, contentDescription = "注销", tint = MaterialTheme.colorScheme.outline) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySelector(label: String, options: List<FormatOption>, selectedOption: FormatOption?, onOptionSelected: (FormatOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedOption?.label ?: "无可用选项",
            onValueChange = {}, readOnly = true, label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option.label) }, onClick = { onOptionSelected(option); expanded = false }) }
        }
    }
}