package com.example.bilidownloader.features.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.bilidownloader.core.database.HistoryEntity
import com.example.bilidownloader.core.database.UserEntity
import com.example.bilidownloader.core.model.CloudHistoryItem
import com.example.bilidownloader.core.model.PageData
import com.example.bilidownloader.di.AppViewModelProvider
import com.example.bilidownloader.features.home.components.BiliWebPlayer
import com.example.bilidownloader.features.home.components.HistoryItem

/**
 * 首页主屏幕 (HomeScreen).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory),
    onNavigateToTranscribe: (String, String) -> Unit, // (path, title)
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Android 13+ 通知权限请求
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { /* 权限结果处理 */ }
    )
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // --- 状态收集 ---
    val state by viewModel.state.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val userList by viewModel.userList.collectAsState()
    val historyList by viewModel.historyList.collectAsState()

    val historyTab by viewModel.historyTab.collectAsState()
    val cloudHistoryList by viewModel.cloudHistoryList.collectAsState()
    val isCloudHistoryLoading by viewModel.isCloudHistoryLoading.collectAsState()
    val cloudHistoryError by viewModel.cloudHistoryError.collectAsState()


    // --- UI 内部状态 ---
    var inputText by remember { mutableStateOf("") }
    var isSelectionMode by remember { mutableStateOf(false) } 
    val selectedItems = remember { mutableStateListOf<HistoryEntity>() }

    // 弹窗控制
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

    // =========================================================
    // 弹窗 (Dialogs)
    // =========================================================

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
                            Icon(
                                Icons.Default.Add,
                                null
                            ); Spacer(modifier = Modifier.width(4.dp)); Text("手动添加")
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

    if (showManualDialog) {
        ManualDialog(onDismiss = { showManualDialog = false })
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (isSelectionMode) "已选 ${selectedItems.size} 项" else "B 站下载器") },
                navigationIcon = {
                    if (state !is HomeState.Idle && !isSelectionMode) {
                        IconButton(onClick = { viewModel.reset() }) {
                            Icon(
                                Icons.Default.ArrowBack,
                                "返回"
                            )
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
                            text = { Text("本地记录") }
                        )
                        Tab(
                            selected = historyTab == HistoryTab.Cloud,
                            onClick = { viewModel.selectHistoryTab(HistoryTab.Cloud) },
                            text = { Text("账号记录") }
                        )
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
                                                } else viewModel.analyzeInput(history.bvid)
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
                                onLoginClick = { showManualCookieInput = true }
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
                        BiliWebPlayer(bvid = currentState.detail.bvid)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(currentState.detail.title, style = MaterialTheme.typography.titleMedium)

                        // [新增] 分P选择器
                        if (currentState.detail.pages.size > 1) {
                            Spacer(modifier = Modifier.height(8.dp))
                            PageSelector(
                                pages = currentState.detail.pages,
                                selectedPage = currentState.selectedPage,
                                onPageSelected = { viewModel.updateSelectedPage(it) }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        QualitySelector("视频画质", currentState.videoFormats, currentState.selectedVideo) { viewModel.updateSelectedVideo(it) }
                        Spacer(modifier = Modifier.height(8.dp))
                        QualitySelector("音频音质", currentState.audioFormats, currentState.selectedAudio) { viewModel.updateSelectedAudio(it) }
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(onClick = { viewModel.startDownload(false) }, modifier = Modifier.fillMaxWidth()) { Text("下载 MP4") }
                        OutlinedButton(
                            onClick = { viewModel.startDownload(true) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) { Text("仅下载音频") }
                        Spacer(modifier = Modifier.height(16.dp))
                        FilledTonalButton(
                            onClick = { showSubtitleDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp)); Spacer(
                            Modifier.width(8.dp)
                        ); Text("AI 智能摘要 / 字幕")
                        }
                        Spacer(modifier = Modifier.height(48.dp))
                    }
                }

                is HomeState.Processing -> {
                    Column(
                        modifier = Modifier
                            .padding(top = 100.dp)
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(currentState.info, style = MaterialTheme.typography.bodyLarge)
                        LinearProgressIndicator(
                            progress = { currentState.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
                        )
                        Text("${(currentState.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                        Spacer(modifier = Modifier.height(32.dp))
                        val isDownloadingPhase = currentState.progress < 0.9f
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

/**
 * 分P选择器组件 (可折叠).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PageSelector(
    pages: List<PageData>,
    selectedPage: PageData,
    onPageSelected: (PageData) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            onClick = { expanded = !expanded },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.List, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "当前分P: P${selectedPage.page} ${selectedPage.part}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column {
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    pages.forEach { page ->
                        val isSelected = page.cid == selectedPage.cid
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                onPageSelected(page)
                                expanded = false
                            },
                            label = { Text("P${page.page}") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }
        }
    }
}

/**
 * AI 摘要与字幕弹窗.
 */
@Composable
fun SubtitleDialog(
    currentState: HomeState.ChoiceSelect,
    viewModel: HomeViewModel,
    onDismiss: () -> Unit,
    onNavigateToTranscribe: (String, String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    if (currentState.subtitleContent.startsWith("ERROR:")) {
        val errorMsg = currentState.subtitleContent.removePrefix("ERROR:")
        LaunchedEffect(errorMsg) {
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG)
                .show(); viewModel.consumeSubtitleError()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier
                .padding(16.dp)
                .fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentState.subtitleData != null) IconButton(onClick = { viewModel.clearSubtitleState() }) {
                            Icon(Icons.Default.ArrowBack, "重选")
                        }
                        else {
                            Icon(
                                Icons.Default.AutoAwesome,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (currentState.subtitleData != null) "字幕详情" else "AI 摘要 & 字幕",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭") }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentState.isSubtitleLoading) CircularProgressIndicator()
                    else if (currentState.subtitleData == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Subtitles, null, Modifier.size(64.dp), MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(16.dp))
                            Text("获取视频的 AI 总结与字幕", style = MaterialTheme.typography.bodyLarge)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { viewModel.fetchSubtitle() }) { Text("立即获取 (B站 API)") }
                            Spacer(Modifier.height(16.dp))
                            Text("或者", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                            Spacer(Modifier.height(16.dp))

                            OutlinedButton(onClick = {
                                val savedTitle = currentState.detail.title
                                viewModel.prepareForTranscription { path ->
                                    onNavigateToTranscribe(
                                        path,
                                        savedTitle
                                    )
                                }
                            }) { Text("没有字幕？试试阿里云转写") }
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
                if (currentState.subtitleData != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = currentState.isTimestampEnabled,
                                onCheckedChange = { viewModel.toggleTimestamp(it) },
                                modifier = Modifier.scale(0.8f)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("时间轴", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.exportSubtitle(currentState.subtitleContent) }) {
                                Icon(
                                    Icons.Default.SaveAlt,
                                    "导出为TXT",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                clipboardManager.setText(AnnotatedString(currentState.subtitleContent));
                                Toast.makeText(context, "内容已复制", Toast.LENGTH_SHORT).show();
                                onDismiss()
                            }) {
                                Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Spacer(
                                Modifier.width(8.dp)
                            ); Text("复制并关闭")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.CloudHistoryContent(
    viewModel: HomeViewModel,
    currentUser: UserEntity?,
    cloudHistoryList: List<CloudHistoryItem>,
    isCloudHistoryLoading: Boolean,
    cloudHistoryError: String?,
    onLoginClick: () -> Unit
) {
    val cloudListState = rememberLazyListState()

    LaunchedEffect(cloudListState) {
        snapshotFlow { cloudListState.layoutInfo.visibleItemsInfo }
            .collect { visibleItems ->
                if (visibleItems.isNotEmpty() && visibleItems.last().index >= cloudHistoryList.size - 3) {
                    viewModel.loadMoreCloudHistory()
                }
            }
    }

    Box(modifier = Modifier
        .weight(1f)
        .padding(top = 8.dp)
        .fillMaxWidth()) {
        when {
            currentUser == null -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Icon(
                        Icons.Default.PersonOff,
                        null,
                        Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Text("登录后可查看云端播放历史", color = MaterialTheme.colorScheme.outline)
                    Button(onClick = onLoginClick) { Text("添加账号/登录") }
                }
            }
            isCloudHistoryLoading && cloudHistoryList.isEmpty() -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            cloudHistoryError != null && cloudHistoryList.isEmpty() -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("加载失败", color = MaterialTheme.colorScheme.error)
                    Text(cloudHistoryError, color = MaterialTheme.colorScheme.outline)
                    Button(onClick = { viewModel.refreshCloudHistory() }) { Text("重试") }
                }
            }
            else -> {
                LazyColumn(
                    state = cloudListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(cloudHistoryList, key = { it.kid }) { item ->
                        CloudHistoryItem(
                            item = item,
                            onClick = { viewModel.analyzeInput(item.bvid) },
                            onLongClick = { }
                        )
                    }
                    if (isCloudHistoryLoading && cloudHistoryList.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CloudHistoryItem(item: CloudHistoryItem, onClick: () -> Unit, onLongClick: () -> Unit) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(90.dp)
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    clipboardManager.setText(AnnotatedString(item.bvid))
                    Toast.makeText(context, "BV号已复制: ${item.bvid}", Toast.LENGTH_SHORT).show()
                    onLongClick()
                }
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(modifier = Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.cover.replace("http://", "https://"),
                contentDescription = "视频封面",
                modifier = Modifier
                    .width(140.dp)
                    .fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(8.dp)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Column {
                    Text(
                        text = "UP: ${item.author_name}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = item.viewDateText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("用户使用手册") },
                    actions = {
                        IconButton(onClick = onDismiss) {
                            Icon(
                                Icons.Default.Close,
                                "关闭"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        ) { padding ->
            AndroidView(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                factory = { context ->
                    WebView(context).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            allowFileAccess = true
                        }
                        webViewClient = WebViewClient()
                        webChromeClient = WebChromeClient()
                        loadUrl("file:///android_asset/docs/index.html")
                    }
                }
            )
        }
    }
}

fun Modifier.scale(scale: Float): Modifier = this.then(Modifier.graphicsLayer(scaleX = scale, scaleY = scale))

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountItem(user: UserEntity, isCurrent: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user.face,
            contentDescription = "用户头像",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                user.name,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Default.Close,
                "注销",
                tint = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySelector(label: String, options: List<FormatOption>, selectedOption: FormatOption?, onOptionSelected: (FormatOption) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedOption?.label ?: "无可用选项",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { onOptionSelected(option); expanded = false })
            }
        }
    }
}