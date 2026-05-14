package com.example.bilidownloader.features.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.bilidownloader.core.database.HistoryEntity
import com.example.bilidownloader.di.AppViewModelProvider
import com.example.bilidownloader.features.home.components.*

/**
 * 首页主屏幕 (HomeScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
    onNavigateToTranscribe: (String, String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { }
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val state by viewModel.state.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val userList by viewModel.userList.collectAsState()
    val historyList by viewModel.historyList.collectAsState()

    val historyTab by viewModel.historyTab.collectAsState()
    val cloudHistoryList by viewModel.cloudHistoryList.collectAsState()
    val isCloudHistoryLoading by viewModel.isCloudHistoryLoading.collectAsState()
    val cloudHistoryError by viewModel.cloudHistoryError.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<HistoryEntity>() }

    var showAccountDialog by remember { mutableStateOf(false) }
    var showManualCookieInput by remember { mutableStateOf(false) }
    var showSubtitleDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }

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

    BackHandler(enabled = isSelectionMode || state !is HomeState.Idle) {
        if (isSelectionMode) exitSelectionMode()
        else if (state !is HomeState.Idle) viewModel.reset()
    }

    if (showAccountDialog) {
        Dialog(onDismissRequest = { showAccountDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("账号管理", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))

                    if (userList.isEmpty()) {
                        Text("暂无账号，请添加", color = MaterialTheme.colorScheme.secondary)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(userList) { user ->
                                AccountItem(
                                    user = user,
                                    isCurrent = user.mid == currentUser?.mid,
                                    onClick = { if (user.mid != currentUser?.mid) viewModel.switchAccount(user) },
                                    onLongClick = {
                                        var cookieStr = user.sessData.trim()
                                        if (!cookieStr.endsWith(";")) cookieStr += ";"
                                        if (!cookieStr.contains("bili_jct") && user.biliJct.isNotEmpty()) {
                                            cookieStr += " bili_jct=${user.biliJct};"
                                        }
                                        clipboardManager.setText(AnnotatedString(cookieStr))
                                        Toast.makeText(context, "完整 Cookie 已复制", Toast.LENGTH_SHORT).show()
                                    },
                                    onDelete = { viewModel.logoutAndRemove(user) }
                                )
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = {
                            showManualCookieInput = true; showAccountDialog = false
                        }) {
                            Icon(Icons.Default.Add, null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("手动添加")
                        }
                        if (currentUser != null) {
                            TextButton(
                                onClick = {
                                    viewModel.quitToGuestMode(); showAccountDialog = false
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("切换游客") }
                        }
                    }
                    TextButton(
                        onClick = { showAccountDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("关闭") }
                }
            }
        }
    }

    if (showManualCookieInput) {
        var cookieText by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showManualCookieInput = false }) {
            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("添加新账号", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cookieText,
                        onValueChange = { cookieText = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp, max = 220.dp),
                        placeholder = { Text("粘贴 SESSDATA=xxx; bili_jct=yyy;") },
                        textStyle = MaterialTheme.typography.bodySmall,
                        minLines = 3
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = {
                            showManualCookieInput = false; onNavigateToLogin()
                        }) { Text("短信登录") }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { showManualCookieInput = false }) { Text("取消") }
                        Button(
                            onClick = {
                                viewModel.addOrUpdateAccount(cookieText); showManualCookieInput =
                                false; showAccountDialog = true
                            },
                            enabled = cookieText.isNotBlank()
                        ) { Text("添加") }
                    }
                }
            }
        }
    }

    if (showSubtitleDialog && state is HomeState.ChoiceSelect) {
        SubtitleDialog(
            currentState = state as HomeState.ChoiceSelect,
            viewModel = viewModel,
            onDismiss = { showSubtitleDialog = false },
            onNavigateToTranscribe = { path, title ->
                showSubtitleDialog = false
                onNavigateToTranscribe(path, title)
            }
        )
    }

    if (showManualDialog) ManualDialog(onDismiss = { showManualDialog = false })

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (isSelectionMode) "已选 ${selectedItems.size} 项" else "B 站下载器") },
                navigationIcon = {
                    if (state !is HomeState.Idle && !isSelectionMode) {
                        IconButton(onClick = { viewModel.reset() }) {
                            Icon(Icons.Default.ArrowBack, "返回")
                        }
                    }
                },
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = { viewModel.deleteHistories(selectedItems.toList()); exitSelectionMode() }) {
                            Icon(Icons.Default.Delete, "删除")
                        }
                    } else {
                        IconButton(onClick = { showManualDialog = true }) {
                            Icon(
                                Icons.Default.HelpOutline,
                                "使用手册",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = { showAccountDialog = true }) {
                            if (currentUser != null) {
                                AsyncImage(
                                    model = currentUser?.face,
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
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
                is HomeState.Idle -> {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("粘贴 B 站链接或文字") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3, maxLines = 6,
                        trailingIcon = {
                            if (inputText.isNotEmpty()) IconButton(onClick = {
                                inputText = ""
                            }) { Icon(Icons.Default.Close, "清空") }
                        }
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.analyzeInput(inputText) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        enabled = inputText.isNotBlank()
                    ) { Text("开始解析") }
                    Spacer(modifier = Modifier.height(24.dp))
                    TabRow(
                        selectedTabIndex = historyTab.ordinal,
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = historyTab == HistoryTab.Local,
                            onClick = { viewModel.selectHistoryTab(HistoryTab.Local) },
                            text = { Text("本地记录") })
                        Tab(
                            selected = historyTab == HistoryTab.Cloud,
                            onClick = { viewModel.selectHistoryTab(HistoryTab.Cloud) },
                            text = { Text("账号记录") })
                    }
                    when (historyTab) {
                        HistoryTab.Local -> {
                            if (historyList.isNotEmpty()) {
                                LazyColumn(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(top = 8.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(historyList) { history ->
                                        HistoryItem(
                                            history = history,
                                            isSelectionMode = isSelectionMode,
                                            isSelected = selectedItems.contains(history),
                                            onClick = {
                                                if (isSelectionMode) {
                                                    if (selectedItems.contains(history)) selectedItems.remove(
                                                        history
                                                    )
                                                    else selectedItems.add(history)
                                                } else {
                                                    inputText = history.bvid
                                                }
                                            },
                                            onLongClick = {
                                                if (!isSelectionMode) {
                                                    isSelectionMode = true; selectedItems.add(
                                                        history
                                                    )
                                                }
                                            }
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "暂无本地解析记录",
                                        color = MaterialTheme.colorScheme.outline
                                    )
                                }
                            }
                        }
                        HistoryTab.Cloud -> {
                            CloudHistoryContent(
                                viewModel = viewModel,
                                currentUser = currentUser,
                                cloudHistoryList = cloudHistoryList,
                                isCloudHistoryLoading = isCloudHistoryLoading,
                                cloudHistoryError = cloudHistoryError,
                                onLoginClick = { showManualCookieInput = true },
                                onItemClick = { bvid -> inputText = bvid }
                            )
                        }
                    }
                }
                is HomeState.Analyzing -> CircularProgressIndicator(modifier = Modifier.padding(top = 100.dp))
                is HomeState.ChoiceSelect -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BiliWebPlayer(
                            bvid = currentState.detail.bvid,
                            page = currentState.selectedPage.page
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(currentState.detail.title, style = MaterialTheme.typography.titleMedium)
                        if (currentState.detail.pages.size > 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                            PageSelector(
                                pages = currentState.detail.pages,
                                selectedPage = currentState.selectedPage,
                                onPageSelected = { viewModel.updateSelectedPage(it) })
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        QualitySelector("视频画质", currentState.videoFormats, currentState.selectedVideo) { viewModel.updateSelectedVideo(it) }
                        Spacer(modifier = Modifier.height(8.dp))
                        QualitySelector("音频音质", currentState.audioFormats, currentState.selectedAudio) { viewModel.updateSelectedAudio(it) }

                        val isSourceFlac =
                            currentState.selectedAudio?.codecs?.contains("flac") == true
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "保存格式",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            if (isSourceFlac) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 6.dp
                                        ), verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.MusicNote,
                                            null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "FLAC 原声无损",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            } else {
                                SingleChoiceSegmentedButtonRow(modifier = Modifier.weight(1f)) {
                                    SegmentedButton(
                                        selected = currentState.selectedAudioExtension == "m4a",
                                        onClick = { viewModel.updateAudioExtension("m4a") },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = 0,
                                            count = 2
                                        ),
                                        icon = {}) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("M4A", style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                "极速无损",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                    SegmentedButton(
                                        selected = currentState.selectedAudioExtension == "mp3",
                                        onClick = { viewModel.updateAudioExtension("mp3") },
                                        shape = SegmentedButtonDefaults.itemShape(
                                            index = 1,
                                            count = 2
                                        ),
                                        icon = {}) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("MP3", style = MaterialTheme.typography.bodySmall)
                                            Text(
                                                "兼容性好",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // --- [新增] 自定义裁剪 UI ---
                        Spacer(modifier = Modifier.height(16.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(
                                    alpha = 0.5f
                                )
                            )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.ContentCut,
                                        null,
                                        modifier = Modifier.size(20.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text("自定义裁剪", style = MaterialTheme.typography.titleSmall)
                                    Spacer(Modifier.weight(1f))
                                    Switch(
                                        checked = currentState.isCropEnabled,
                                        onCheckedChange = { viewModel.toggleCrop(it) },
                                        thumbContent = {
                                            if (currentState.isCropEnabled) Icon(
                                                Icons.Default.Check,
                                                null,
                                                Modifier.size(12.dp)
                                            )
                                        }
                                    )
                                }
                                if (currentState.isCropEnabled) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    RangeSlider(
                                        value = currentState.cropStart..currentState.cropEnd,
                                        onValueChange = { range ->
                                            viewModel.updateCropRange(
                                                range.start,
                                                range.endInclusive
                                            )
                                        },
                                        valueRange = 0f..currentState.videoDurationSeconds.toFloat(),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            formatTime(currentState.cropStart.toLong()),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "选中时长: ${formatTime((currentState.cropEnd - currentState.cropStart).toLong())}",
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                        Text(
                                            formatTime(currentState.cropEnd.toLong()),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.startDownload(false) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("下载 MP4 视频") }
                        OutlinedButton(
                            onClick = { viewModel.startDownload(true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            val ext =
                                if (isSourceFlac) "FLAC" else currentState.selectedAudioExtension.uppercase()
                            Text("仅下载音频 (.$ext)")
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = { showSubtitleDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("AI 智能摘要 / 字幕")
                        }
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }
                is HomeState.Processing -> {
                    Column(
                        modifier = Modifier
                            .padding(top = 60.dp)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(currentState.info, style = MaterialTheme.typography.titleMedium)
                        if (!currentState.isMerging) {
                            LinearProgressIndicator(
                                progress = { currentState.progress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .height(8.dp),
                                strokeCap = StrokeCap.Round
                            )
                            currentState.detail?.let {
                                Text(
                                    it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Text(
                                "${(currentState.progress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge
                            )
                        } else {
                            Spacer(modifier = Modifier.height(20.dp))
                            val mergeText =
                                if (currentState.info.contains("裁剪")) "正在裁剪片段..." else if (currentState.info.contains(
                                        "音频"
                                    )
                                ) "正在处理音频格式..." else "音视频封装合并中..."
                            Text(mergeText, style = MaterialTheme.typography.bodyMedium)
                            LinearProgressIndicator(
                                progress = { currentState.mergeProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .height(8.dp),
                                color = MaterialTheme.colorScheme.tertiary,
                                strokeCap = StrokeCap.Round
                            )
                            Text(
                                "${(currentState.mergeProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                        Spacer(modifier = Modifier.height(32.dp))
                        val isDownloadingPhase = !currentState.isMerging
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (currentState.info.contains("暂停")) Button(onClick = { viewModel.resumeDownload() }) {
                                Text(
                                    "继续"
                                )
                            }
                            else OutlinedButton(
                                onClick = { viewModel.pauseDownload() },
                                enabled = isDownloadingPhase
                            ) { Text("暂停") }
                            TextButton(
                                onClick = { viewModel.cancelDownload() },
                                enabled = isDownloadingPhase,
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) { Text("取消") }
                        }
                    }
                }
                is HomeState.Success -> {
                    Column(modifier = Modifier.padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉 ${currentState.message}", color = MaterialTheme.colorScheme.primary)
                        Button(onClick = { viewModel.reset() }, modifier = Modifier.padding(top = 24.dp)) { Text("完成") }
                    }
                }
                is HomeState.Error -> {
                    Column(modifier = Modifier.padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("❌ ${currentState.errorMsg}", color = MaterialTheme.colorScheme.error)
                        Button(onClick = { viewModel.reset() }, modifier = Modifier.padding(top = 24.dp)) { Text("重试") }
                    }
                }
            }
        }
    }
}

private fun formatTime(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
}
