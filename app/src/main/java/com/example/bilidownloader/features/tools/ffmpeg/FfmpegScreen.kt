package com.example.bilidownloader.features.ffmpeg

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.bilidownloader.core.database.FfmpegPresetEntity
import com.example.bilidownloader.di.AppViewModelProvider

/**
 * FFmpeg 万能终端界面.
 * 集成了媒体选择、权限申请、命令执行、控制台输出以及预设管理系统。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FfmpegScreen(
    onBack: () -> Unit,
    viewModel: FfmpegViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // --- 状态控制 ---
    var showMediaSheet by remember { mutableStateOf(false) } // 媒体选择弹窗
    var showPresetSheet by remember { mutableStateOf(false) } // 预设列表弹窗
    var showSaveDialog by remember { mutableStateOf(false) } // 保存预设弹窗
    val isRunning = uiState.taskState is FfmpegTaskState.Running

    // --- 权限请求 Launcher ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            showMediaSheet = true
        } else {
            Toast.makeText(context, "需要存储权限才能读取媒体文件", Toast.LENGTH_SHORT).show()
        }
    }

    // 检查并打开媒体选择器
    fun checkAndOpenPicker() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

        val allGranted = permissions.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        if (allGranted) {
            showMediaSheet = true
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    // 监听任务状态
    LaunchedEffect(uiState.taskState) {
        when (val state = uiState.taskState) {
            is FfmpegTaskState.Success -> Toast.makeText(context, "处理完成！耗时: ${state.costTime}ms", Toast.LENGTH_LONG).show()
            is FfmpegTaskState.Error -> Toast.makeText(context, "出错: ${state.message}", Toast.LENGTH_SHORT).show()
            else -> {}
        }
    }

    // --- 保存预设弹窗 ---
    if (showSaveDialog) {
        SavePresetDialog(
            currentArgs = uiState.arguments,
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                viewModel.saveCurrentAsPreset(name)
                showSaveDialog = false
                Toast.makeText(context, "预设已保存", Toast.LENGTH_SHORT).show()
            }
        )
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Terminal")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                },
                actions = {
                    // [新增] 保存当前命令为预设
                    IconButton(onClick = { showSaveDialog = true }, enabled = uiState.arguments.isNotBlank()) {
                        Icon(Icons.Default.Save, contentDescription = "保存预设")
                    }
                    // [新增] 打开预设列表
                    IconButton(onClick = { showPresetSheet = true }) {
                        Icon(Icons.Default.Bookmarks, contentDescription = "预设列表")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color(0xFF00FF00),
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            // 1. Input Slot
            FileSelectionCard(
                fileName = uiState.inputFileName,
                fileSize = uiState.inputFileSize,
                mediaInfo = uiState.mediaInfo,
                enabled = !isRunning,
                onSelectClick = { checkAndOpenPicker() }
            )

            Divider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            // 2. Command Builder
            CommandBuilderArea(
                uiState = uiState,
                onArgsChange = { viewModel.onArgumentsChanged(it) },
                onExtChange = { viewModel.onExtensionChanged(it) },
                onExecute = {
                    focusManager.clearFocus()
                    if (isRunning) viewModel.stopCommand() else viewModel.executeCommand()
                },
                isRunning = isRunning
            )

            // 3. Terminal Console
            TerminalConsole(
                taskState = uiState.taskState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }

        // --- 媒体选择底部弹窗 ---
        if (showMediaSheet) {
            MediaPickerBottomSheet(
                viewModel = viewModel,
                onDismiss = { showMediaSheet = false },
                onMediaSelected = { media ->
                    viewModel.onFileSelected(media.uri)
                    showMediaSheet = false
                }
            )
        }

        // --- 预设列表底部弹窗 ---
        if (showPresetSheet) {
            PresetListBottomSheet(
                viewModel = viewModel,
                onDismiss = { showPresetSheet = false },
                onApply = { preset ->
                    viewModel.applyPreset(preset)
                    showPresetSheet = false
                    Toast.makeText(context, "已加载预设: ${preset.name}", Toast.LENGTH_SHORT).show()
                }
            )
        }
    }
}

// region --- 预设相关组件 ---

@Composable
fun SavePresetDialog(
    currentArgs: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存为预设") },
        text = {
            Column {
                Text("将当前参数保存以便日后快速调用。", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("预设名称") },
                    placeholder = { Text("例如：提取音频MP3") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("预览: ${currentArgs.take(50)}...", style = MaterialTheme.typography.labelSmall, color = Color.Gray, maxLines = 1)
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                enabled = name.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetListBottomSheet(
    viewModel: FfmpegViewModel,
    onDismiss: () -> Unit,
    onApply: (FfmpegPresetEntity) -> Unit
) {
    val presetList by viewModel.presetList.collectAsState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1E1E1E) // 深色背景保持风格统一
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.6f).padding(horizontal = 16.dp)) {
            Text(
                text = "指令预设",
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF00FF00), // 绿色标题
                modifier = Modifier.padding(vertical = 16.dp)
            )

            if (presetList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无预设，请先在终端保存命令", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(presetList, key = { it.id }) { preset ->
                        PresetItem(
                            preset = preset,
                            onApply = { onApply(preset) },
                            onDelete = { viewModel.deletePreset(preset) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PresetItem(
    preset: FfmpegPresetEntity,
    onApply: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onApply)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(preset.name, style = MaterialTheme.typography.titleMedium, color = Color.White)
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 后缀名徽章
                    Surface(
                        color = Color(0xFF00FF00).copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Text(
                            text = preset.outputExtension,
                            color = Color(0xFF00FF00),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = preset.commandArgs,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "删除", tint = Color.Gray)
            }
        }
    }
}

// endregion

// region --- 核心组件 (CommandBuilder, Console, MediaPicker) ---

@Composable
private fun FileSelectionCard(
    fileName: String,
    fileSize: String,
    mediaInfo: String,
    enabled: Boolean,
    onSelectClick: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧可点击区域
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = enabled, onClick = onSelectClick),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (fileName.isEmpty()) Color.Gray else MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (fileName.isEmpty()) Icons.Default.Add else Icons.Default.VideoFile,
                        contentDescription = null,
                        tint = if (fileName.isEmpty()) Color.White else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    if (fileName.isEmpty()) {
                        Text("选择输入文件", style = MaterialTheme.typography.titleMedium)
                        Text("支持视频/音频格式", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else {
                        Text(fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                        Text("大小: $fileSize", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // 右侧信息按钮
            if (fileName.isNotEmpty()) {
                IconButton(
                    onClick = {
                        if (mediaInfo.isNotBlank()) {
                            clipboardManager.setText(AnnotatedString(mediaInfo))
                            Toast.makeText(context, "详细参数已复制 (JSON)", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "正在分析文件信息...", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "复制详细信息",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
            }
        }
    }
}

@Composable
private fun CommandBuilderArea(
    uiState: FfmpegUiState,
    onArgsChange: (String) -> Unit,
    onExtChange: (String) -> Unit,
    onExecute: () -> Unit,
    isRunning: Boolean
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("COMMAND PREVIEW", style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF00FF00), fontWeight = FontWeight.Bold)) { append("ffmpeg ") }
                withStyle(SpanStyle(color = Color.Gray)) { append("-i input_file ") }
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        )

        OutlinedTextField(
            value = uiState.arguments,
            onValueChange = onArgsChange,
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp),
            placeholder = { Text("-c:v libx264 ...") },
            enabled = !isRunning,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            minLines = 1, maxLines = 5,
            trailingIcon = {
                if (uiState.arguments.isNotEmpty() && !isRunning) {
                    IconButton(onClick = { onArgsChange("") }) {
                        Icon(Icons.Default.Close, "清空", tint = Color.Gray)
                    }
                }
            }
        )

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("output_file", fontFamily = FontFamily.Monospace, color = Color.Gray, fontSize = 14.sp)
            OutlinedTextField(
                value = uiState.outputExtension,
                onValueChange = onExtChange,
                modifier = Modifier.width(100.dp).padding(start = 8.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                singleLine = true,
                enabled = !isRunning,
                label = { Text("后缀") }
            )
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = onExecute,
                enabled = uiState.inputFileUri != null,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isRunning) Color(0xFFB00020) else Color(0xFF1E1E1E),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                if (isRunning) {
                    Icon(Icons.Default.Stop, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("STOP")
                } else {
                    Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("RUN")
                }
            }
        }
    }
}

@Composable
private fun TerminalConsole(taskState: FfmpegTaskState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val logs = when (taskState) {
        is FfmpegTaskState.Running -> taskState.logs
        is FfmpegTaskState.Success -> taskState.logs
        is FfmpegTaskState.Error -> taskState.logs
        else -> emptyList()
    }

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1)
    }

    Column(modifier = modifier) {
        if (taskState is FfmpegTaskState.Running) {
            LinearProgressIndicator(
                progress = { if (taskState.progress < 0) 0f else taskState.progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Color(0xFF00FF00),
                trackColor = Color.Black
            )
        }

        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF1E1E1E)).padding(8.dp)) {
            if (logs.isEmpty()) {
                Text("> Waiting for command...", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.align(Alignment.Center))
            } else {
                SelectionContainer {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 48.dp)
                    ) {
                        items(logs) { log ->
                            Text(
                                text = log,
                                color = when {
                                    log.contains("Error") || log.contains("错误") -> Color.Red
                                    log.contains("Success") || log.contains("成功") -> Color(0xFF00FF00)
                                    log.startsWith(">>>") -> Color.Cyan
                                    else -> Color.LightGray
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        val fullLog = logs.joinToString("\n")
                        clipboardManager.setText(AnnotatedString(fullLog))
                        Toast.makeText(context, "完整日志已复制", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(32.dp).background(Color.White.copy(0.1f), RoundedCornerShape(4.dp))
                ) {
                    Icon(Icons.Default.ContentCopy, "复制", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPickerBottomSheet(viewModel: FfmpegViewModel, onDismiss: () -> Unit, onMediaSelected: (LocalMedia) -> Unit) {
    val mediaList by viewModel.localMediaList.collectAsState()
    val isLoading by viewModel.isMediaLoading.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    LaunchedEffect(selectedTab) { viewModel.loadLocalMedia(selectedTab == 0) }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Color(0xFF1E1E1E)) {
        Column(modifier = Modifier.fillMaxHeight(0.7f).padding(bottom = 16.dp)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color(0xFF00FF00),
                indicator = { TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(it[selectedTab]), color = Color(0xFF00FF00)) }
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("视频", color = if(selectedTab==0) Color(0xFF00FF00) else Color.Gray) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("音频", color = if(selectedTab==1) Color(0xFF00FF00) else Color.Gray) })
            }
            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                if (isLoading) CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF00FF00))
                else if (mediaList.isEmpty()) Text("暂无媒体文件", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                else {
                    if (selectedTab == 0) {
                        LazyVerticalGrid(GridCells.Fixed(3), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            items(mediaList) { VideoGridItem(it) { onMediaSelected(it) } }
                        }
                    } else {
                        LazyColumn(Modifier.fillMaxSize()) {
                            items(mediaList) { AudioListItem(it) { onMediaSelected(it) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VideoGridItem(media: LocalMedia, onClick: () -> Unit) {
    Box(modifier = Modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Color.DarkGray).clickable(onClick = onClick)) {
        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(media.uri).decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }.videoFrameMillis(1000).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Text(formatDuration(media.duration), color = Color.White, fontSize = 10.sp, modifier = Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(0.6f)).padding(horizontal = 4.dp, vertical = 2.dp))
    }
}

@Composable
fun AudioListItem(media: LocalMedia, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.AudioFile, null, tint = Color(0xFF00FF00), modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(media.name, color = Color.White, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            Text("${formatDuration(media.duration)} | ${(media.size/1024/1024.0).let{"%.1f MB".format(it)}}", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
        }
    }
}

private fun formatDuration(ms: Long) = "%02d:%02d".format(ms / 1000 / 60, ms / 1000 % 60)

// endregion