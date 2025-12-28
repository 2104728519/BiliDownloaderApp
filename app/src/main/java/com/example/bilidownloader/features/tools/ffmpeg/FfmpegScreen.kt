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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.bilidownloader.di.AppViewModelProvider

/**
 * FFmpeg 万能终端界面.
 *
 * 集成了媒体选择弹窗、动态权限申请、FFmpeg 命令实时预览与控制台输出。
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
    var showBottomSheet by remember { mutableStateOf(false) }
    val isRunning = uiState.taskState is FfmpegTaskState.Running

    // --- 权限请求 Launcher ---
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.any { it }) {
            showBottomSheet = true
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
            showBottomSheet = true
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    // 监听任务状态变化，弹出提示
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

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Terminal, null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("FFmpeg Terminal")
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "返回") }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color(0xFF1E1E1E),
                    titleContentColor = Color(0xFF00FF00),
                    navigationIconContentColor = Color.White
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
            // 1. Input Slot (文件选择区)
            FileSelectionCard(
                fileName = uiState.inputFileName,
                fileSize = uiState.inputFileSize,
                enabled = !isRunning,
                onSelectClick = { checkAndOpenPicker() }
            )

            Divider(thickness = 8.dp, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))

            // 2. Command Builder (命令构建区)
            CommandBuilderArea(
                uiState = uiState,
                onArgsChange = { viewModel.onArgumentsChanged(it) },
                onExtChange = { viewModel.onExtensionChanged(it) },
                onExecute = {
                    focusManager.clearFocus()
                    viewModel.executeCommand()
                },
                isRunning = isRunning
            )

            // 3. Terminal Console (日志控制台)
            TerminalConsole(
                taskState = uiState.taskState,
                modifier = Modifier.weight(1f).fillMaxWidth()
            )
        }

        // --- 媒体选择底部弹窗 ---
        if (showBottomSheet) {
            MediaPickerBottomSheet(
                viewModel = viewModel,
                onDismiss = { showBottomSheet = false },
                onMediaSelected = { media ->
                    viewModel.onFileSelected(media.uri)
                    showBottomSheet = false
                }
            )
        }
    }
}

/**
 * 组件：媒体选择底部弹窗
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

    LaunchedEffect(selectedTab) {
        viewModel.loadLocalMedia(isVideo = (selectedTab == 0))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF1E1E1E)
    ) {
        Column(modifier = Modifier.fillMaxHeight(0.7f).padding(bottom = 16.dp)) {
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
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 },
                    text = { Text("视频", color = if(selectedTab == 0) Color(0xFF00FF00) else Color.Gray) })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 },
                    text = { Text("音频", color = if(selectedTab == 1) Color(0xFF00FF00) else Color.Gray) })
            }

            Box(modifier = Modifier.fillMaxSize().padding(8.dp)) {
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
                            items(mediaList) { media ->
                                VideoGridItem(media) { onMediaSelected(media) }
                            }
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(mediaList) { media ->
                                AudioListItem(media) { onMediaSelected(media) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 视频网格项组件：带视频帧预览
 */
@Composable
fun VideoGridItem(media: LocalMedia, onClick: () -> Unit) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.DarkGray)
            .clickable(onClick = onClick)
    ) {
        // [修改后的 AsyncImage]：支持视频帧解码
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(media.uri)
                // 强制使用 VideoFrameDecoder 解析视频帧
                .decoderFactory { result, options, _ ->
                    VideoFrameDecoder(result.source, options)
                }
                // 截取第 1000 毫秒 (1秒) 的画面，防止第一帧是黑屏
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
                .background(Color.Black.copy(alpha = 0.6f))
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
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(media.name, color = Color.White, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            Text(
                "${formatDuration(media.duration)} | ${(media.size / 1024 / 1024.0).let { "%.1f MB".format(it) }}",
                color = Color.Gray,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

private fun formatDuration(ms: Long): String {
    val seconds = ms / 1000
    return "%02d:%02d".format(seconds / 60, seconds % 60)
}

// --- 基础组件 ---

@Composable
private fun FileSelectionCard(
    fileName: String,
    fileSize: String,
    enabled: Boolean,
    onSelectClick: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onSelectClick)
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp))
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
            Column(modifier = Modifier.weight(1f)) {
                if (fileName.isEmpty()) {
                    Text("选择输入文件", style = MaterialTheme.typography.titleMedium)
                    Text("点击从相册/媒体库选择", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                } else {
                    Text(fileName, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                    Text("大小: $fileSize", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            Icon(Icons.Default.ChevronRight, null, tint = Color.Gray)
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
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
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
                enabled = !isRunning && uiState.inputFileUri != null,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E), contentColor = Color(0xFF00FF00)),
                shape = RoundedCornerShape(4.dp)
            ) {
                if (isRunning) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color(0xFF00FF00), strokeWidth = 2.dp)
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
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
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
        }
    }
}