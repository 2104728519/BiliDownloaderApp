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
 * FFmpeg 万能终端界面 (Terminal UI).
 *
 * 这是一个集成了“文件选择 -> 命令构建 -> 终端执行 -> 预设管理”的一站式工作台。
 *
 * 核心特性：
 * 1. **Raw Mode**: 可视化展示 FFmpeg 命令结构 (Input -> Args -> Output)。
 * 2. **Media Picker**: 内置底部弹窗式媒体选择器，支持视频/音频分类浏览。
 * 3. **Console**: 实时滚动的黑客风控制台，支持日志选择与复制。
 * 4. **Preset System**: 支持将当前命令保存为预设，或从网络导入预设。
 * 5. **Background Service**: 支持后台运行与随时停止。
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

    // --- UI 状态控制 ---
    var showMediaSheet by remember { mutableStateOf(false) }  // 媒体选择器弹窗
    var showPresetSheet by remember { mutableStateOf(false) } // 预设列表弹窗
    var showSaveDialog by remember { mutableStateOf(false) }  // 保存预设弹窗

    // 判断任务是否正在运行 (用于禁用/启用按钮)
    val isRunning = uiState.taskState is FfmpegTaskState.Running

    // --- 权限请求逻辑 (适配 Android 13+) ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // 只要有一个权限被授予，就打开选择器 (部分授权也允许)
        if (permissions.values.any { it }) {
            showMediaSheet = true
        } else {
            Toast.makeText(context, "需要存储权限才能读取媒体文件", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 检查并请求权限，通过后打开媒体选择器
     */
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

    // --- 监听任务执行结果 (Success/Error Toast) ---
    LaunchedEffect(uiState.taskState) {
        when (val state = uiState.taskState) {
            is FfmpegTaskState.Success -> {
                Toast.makeText(context, "处理完成！耗时: ${state.costTime}ms", Toast.LENGTH_LONG).show()
            }
            is FfmpegTaskState.Error -> {
                Toast.makeText(context, "出错: ${state.message}", Toast.LENGTH_SHORT).show()
            }
            else -> {}
        }
    }

    // --- 显示保存预设对话框 ---
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
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = Color(0xFF00FF00) // 绿色图标，极客风
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Terminal")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 保存当前命令
                    IconButton(
                        onClick = { showSaveDialog = true },
                        enabled = uiState.arguments.isNotBlank()
                    ) {
                        Icon(Icons.Default.Save, contentDescription = "保存预设")
                    }
                    // 打开预设管理
                    IconButton(onClick = { showPresetSheet = true }) {
                        Icon(Icons.Default.Bookmarks, contentDescription = "预设列表")
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1E1E1E), // 深色顶栏
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
            // 1. Input Slot (输入文件区域)
            FileSelectionCard(
                fileName = uiState.inputFileName,
                fileSize = uiState.inputFileSize,
                mediaInfo = uiState.mediaInfo,
                enabled = !isRunning,
                onSelectClick = { checkAndOpenPicker() }
            )

            Divider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            // 2. Command Builder (命令构建与执行区域)
            CommandBuilderArea(
                uiState = uiState,
                onArgsChange = { viewModel.onArgumentsChanged(it) },
                onExtChange = { viewModel.onExtensionChanged(it) },
                onExecute = {
                    focusManager.clearFocus()
                    if (isRunning) {
                        viewModel.stopCommand()
                    } else {
                        viewModel.executeCommand()
                    }
                },
                isRunning = isRunning
            )

            // 3. Terminal Console (控制台区域，占据剩余空间)
            TerminalConsole(
                taskState = uiState.taskState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )
        }

        // --- 底部弹窗逻辑 ---

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

// region --- 预设管理组件 (Preset Management Components) ---

/**
 * 预设列表底部弹窗.
 * 支持导入、导出、复制预设，以及点击应用或删除。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PresetListBottomSheet(
    viewModel: FfmpegViewModel,
    onDismiss: () -> Unit,
    onApply: (FfmpegPresetEntity) -> Unit
) {
    val presetList by viewModel.presetList.collectAsState()
    var showImportDialog by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    // 内部弹窗：输入导入链接
    if (showImportDialog) {
        ImportUrlDialog(
            onDismiss = { showImportDialog = false },
            onConfirm = { url ->
                viewModel.importPresetsFromUrl(url)
                showImportDialog = false
            }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1E1E1E) // 保持深色风格
    ) {
        Column(modifier = Modifier
            .fillMaxHeight(0.7f)
            .padding(horizontal = 16.dp)) {
            // --- 标题与操作栏 ---
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "指令预设",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color(0xFF00FF00)
                )

                // 操作按钮组
                Row {
                    TextButton(onClick = {
                        viewModel.getPresetsJson { json ->
                            if (!json.isNullOrBlank()) {
                                clipboardManager.setText(AnnotatedString(json))
                                Toast.makeText(context, "预设 JSON 已复制", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "暂无预设可复制", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp), tint = Color.Gray)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制", color = Color.Gray)
                    }

                    TextButton(onClick = { showImportDialog = true }) {
                        Icon(Icons.Default.CloudDownload, null, modifier = Modifier.size(18.dp), tint = Color(0xFF00FF00))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导入", color = Color(0xFF00FF00))
                    }

                    TextButton(onClick = { viewModel.exportPresets() }) {
                        Icon(Icons.Default.Output, null, modifier = Modifier.size(18.dp), tint = Color.Cyan)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("导出", color = Color.Cyan)
                    }
                }
            }

            Divider(color = Color.Gray.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(12.dp))

            // --- 列表渲染 ---
            if (presetList.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无预设，请保存或导入", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
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

/**
 * 远程导入链接输入框
 */
@Composable
fun ImportUrlDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("从网络导入预设") },
        text = {
            Column {
                Text("请输入 JSON 配置文件的直链地址：", style = MaterialTheme.typography.bodySmall)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL 地址") },
                    placeholder = { Text("https://example.com/presets.json") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(url) }, enabled = url.isNotBlank()) { Text("开始导入") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 预设列表项
 */
@Composable
fun PresetItem(preset: FfmpegPresetEntity, onApply: () -> Unit, onDelete: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onApply)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
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

@Composable
fun SavePresetDialog(currentArgs: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("保存当前参数") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("预设名称") },
                    placeholder = { Text("例如：极速压缩") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("参数预览: ${currentArgs.take(40)}...", color = Color.Gray, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

// endregion

// region --- 核心 UI 组件 (Core UI Components) ---

/**
 * 文件选择卡片
 */
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
            // 左侧点击区域：图标 + 文本
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
                        Text("视频/音频/GIF", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    } else {
                        Text(fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("大小: $fileSize", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            // 右侧信息按钮：复制详细参数
            if (fileName.isNotEmpty()) {
                IconButton(onClick = {
                    if (mediaInfo.isNotBlank()) {
                        clipboardManager.setText(AnnotatedString(mediaInfo))
                        Toast.makeText(context, "详细参数已复制 (JSON)", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "正在分析文件信息...", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "详细信息",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
            }
        }
    }
}

/**
 * 命令构建区域：包含参数输入框、后缀名输入框和执行按钮
 */
@Composable
private fun CommandBuilderArea(
    uiState: FfmpegUiState,
    onArgsChange: (String) -> Unit,
    onExtChange: (String) -> Unit,
    onExecute: () -> Unit,
    isRunning: Boolean
) {
    Column(modifier = Modifier.padding(16.dp)) {
        Text("COMMAND BUILDER", style = MaterialTheme.typography.labelSmall, color = Color.Gray, letterSpacing = 2.sp)
        Spacer(modifier = Modifier.height(8.dp))

        // 模拟命令行 Header
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = Color(0xFF00FF00), fontWeight = FontWeight.Bold)) { append("ffmpeg ") }
                withStyle(SpanStyle(color = Color.Gray)) { append("-i input ") }
            },
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp
        )

        // 参数输入框
        OutlinedTextField(
            value = uiState.arguments,
            onValueChange = onArgsChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 16.sp),
            placeholder = { Text("-c:v copy ...") },
            enabled = !isRunning,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            minLines = 1,
            maxLines = 5,
            trailingIcon = {
                if (uiState.arguments.isNotEmpty() && !isRunning) {
                    IconButton(onClick = { onArgsChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "清空", tint = Color.Gray)
                    }
                }
            }
        )

        // 后缀与按钮
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = uiState.outputExtension,
                onValueChange = onExtChange,
                modifier = Modifier
                    .width(90.dp)
                    .padding(start = 8.dp),
                textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace, fontSize = 14.sp),
                singleLine = true,
                enabled = !isRunning,
                label = { Text("后缀") }
            )

            Spacer(modifier = Modifier.weight(1f))

            // 运行/停止按钮
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

/**
 * 终端控制台：显示日志和进度
 */
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

    // 自动滚动
    LaunchedEffect(logs.size) { if (logs.isNotEmpty()) listState.animateScrollToItem(logs.size - 1) }

    Column(modifier = modifier) {
        // 进度条
        if (taskState is FfmpegTaskState.Running) {
            LinearProgressIndicator(
                progress = { if (taskState.progress < 0) 0f else taskState.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = Color(0xFF00FF00),
                trackColor = Color.Black
            )
        }

        // 日志区域
        Box(modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(8.dp)) {
            if (logs.isEmpty()) {
                Text(
                    "> Initializing Terminal...",
                    color = Color.Gray,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.Center)
                )
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

                // 复制按钮
                IconButton(
                    onClick = {
                        clipboardManager.setText(AnnotatedString(logs.joinToString("\n")))
                        Toast.makeText(context, "日志已复制", Toast.LENGTH_SHORT).show()
                    },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(32.dp)
                        .background(Color.White.copy(0.1f), RoundedCornerShape(4.dp))
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "复制", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/**
 * 媒体选择底部弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaPickerBottomSheet(
    viewModel: FfmpegViewModel,
    onDismiss: () -> Unit,
    onMediaSelected: (LocalMedia) -> Unit
) {
    val mediaList by viewModel.localMediaList.collectAsState()
    val isLoading by viewModel.isMediaLoading.collectAsState()
    var selectedTab by remember { mutableStateOf(0) } // 0=Video, 1=Audio

    LaunchedEffect(selectedTab) { viewModel.loadLocalMedia(selectedTab == 0) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1E1E1E)
    ) {
        Column(modifier = Modifier
            .fillMaxHeight(0.7f)
            .padding(bottom = 16.dp)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF1E1E1E),
                contentColor = Color(0xFF00FF00),
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color(0xFF00FF00)
                    )
                }
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("视频", color = if(selectedTab==0) Color(0xFF00FF00) else Color.Gray) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("音频", color = if(selectedTab==1) Color(0xFF00FF00) else Color.Gray) })
            }

            Box(modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF00FF00))
                } else if (mediaList.isEmpty()) {
                    Text("暂无媒体文件", modifier = Modifier.align(Alignment.Center), color = Color.Gray)
                } else {
                    if (selectedTab == 0) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(mediaList) { VideoGridItem(it) { onMediaSelected(it) } }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
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
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .clickable(onClick = onClick)
    ) {
        // 使用 Coil 的视频解码器显示缩略图
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(media.uri)
                .decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
                .videoFrameMillis(1000)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Text(
            text = formatDuration(media.duration),
            color = Color.White,
            fontSize = 10.sp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(Color.Black.copy(0.6f))
                .padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun AudioListItem(media: LocalMedia, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.AudioFile, null, tint = Color(0xFF00FF00), modifier = Modifier.size(32.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(media.name, color = Color.White, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${formatDuration(media.duration)} | ${(media.size/1024/1024.0).let{"%.1f MB".format(it)}}",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatDuration(ms: Long) = "%02d:%02d".format(ms / 1000 / 60, ms / 1000 % 60)

// endregion