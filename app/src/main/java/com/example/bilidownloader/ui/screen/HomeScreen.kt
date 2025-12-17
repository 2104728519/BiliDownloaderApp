package com.example.bilidownloader.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.bilidownloader.data.database.HistoryEntity
import com.example.bilidownloader.data.database.UserEntity
import com.example.bilidownloader.di.AppViewModelProvider // 【关键导入】
import com.example.bilidownloader.ui.components.BiliWebPlayer
import com.example.bilidownloader.ui.components.HistoryItem
import com.example.bilidownloader.ui.state.FormatOption
import com.example.bilidownloader.ui.state.MainState
import com.example.bilidownloader.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    // 【核心修改】：使用自定义 Factory 注入依赖
    viewModel: MainViewModel = viewModel(factory = AppViewModelProvider.Factory),
    onNavigateToTranscribe: (String) -> Unit,
    onNavigateToLogin: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // 状态监听
    val currentUser by viewModel.currentUser.collectAsState()
    val userList by viewModel.userList.collectAsState()
    val state by viewModel.state.collectAsState()
    val historyList by viewModel.historyList.collectAsState()

    var inputText by remember { mutableStateOf("") }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<HistoryEntity>() }

    var showAccountDialog by remember { mutableStateOf(false) }
    var showManualCookieInput by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
    }

    // 生命周期监听：处理从短信登录返回后的同步
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.syncCookieToUserDB()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // 返回键逻辑
    BackHandler(enabled = isSelectionMode || state !is MainState.Idle) {
        if (isSelectionMode) {
            exitSelectionMode()
        } else if (state !is MainState.Idle) {
            viewModel.reset()
        }
    }

    // --- 弹窗逻辑：账号管理 ---
    if (showAccountDialog) {
        Dialog(onDismissRequest = { showAccountDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = MaterialTheme.shapes.large
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
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        val clip = ClipData.newPlainText("BiliCookie", user.sessData)
                                        clipboard.setPrimaryClip(clip)
                                        Toast.makeText(context, "Cookie 已复制: ${user.name}", Toast.LENGTH_SHORT).show()
                                    },
                                    onDelete = { viewModel.logoutAndRemove(user) }
                                )
                                HorizontalDivider()
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        TextButton(onClick = {
                            showManualCookieInput = true
                            showAccountDialog = false
                        }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("添加账号")
                        }

                        if (currentUser != null) {
                            TextButton(
                                onClick = {
                                    viewModel.quitToGuestMode()
                                    showAccountDialog = false
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("切换游客")
                            }
                        }
                    }
                    TextButton(onClick = { showAccountDialog = false }, modifier = Modifier.align(Alignment.End)) {
                        Text("关闭")
                    }
                }
            }
        }
    }

    // --- 弹窗逻辑：手动输入 ---
    if (showManualCookieInput) {
        var cookieText by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showManualCookieInput = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("添加新账号", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = cookieText,
                        onValueChange = { cookieText = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("粘贴 SESSDATA=xxx;") },
                        minLines = 3
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = {
                            showManualCookieInput = false
                            onNavigateToLogin()
                        }) {
                            Text("短信登录")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { showManualCookieInput = false }) { Text("取消") }
                        Button(onClick = {
                            viewModel.addOrUpdateAccount(cookieText)
                            showManualCookieInput = false
                            showAccountDialog = true
                        }, enabled = cookieText.isNotBlank()) {
                            Text("添加")
                        }
                    }
                }
            }
        }
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
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
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
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 主内容区域（根据 State 切换展示内容）
            when (val currentState = state) {
                is MainState.Idle -> {
                    // 首页：输入框 + 历史记录
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        label = { Text("粘贴视频链接") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.analyzeInput(inputText) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        enabled = inputText.isNotBlank()
                    ) {
                        Text("解析视频")
                    }

                    if (historyList.isNotEmpty() && !isSelectionMode) {
                        Text("历史记录", style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.Start))
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(historyList) { history ->
                            HistoryItem(
                                history = history,
                                isSelectionMode = isSelectionMode,
                                isSelected = selectedItems.contains(history),
                                onClick = {
                                    if (isSelectionMode) {
                                        if (selectedItems.contains(history)) {
                                            selectedItems.remove(history)
                                            if (selectedItems.isEmpty()) isSelectionMode = false
                                        } else {
                                            selectedItems.add(history)
                                        }
                                    } else {
                                        viewModel.analyzeInput(history.bvid)
                                    }
                                },
                                onLongClick = {
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedItems.add(history)
                                    }
                                }
                            )
                        }
                    }
                }

                is MainState.Analyzing -> {
                    CircularProgressIndicator(modifier = Modifier.padding(top = 100.dp))
                }

                is MainState.ChoiceSelect -> {
                    // 解析成功后的选择下载界面
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

                        Button(
                            onClick = { viewModel.prepareForTranscription { onNavigateToTranscribe(it) } },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI 转文字")
                        }
                    }
                }

                is MainState.Processing -> {
                    Column(modifier = Modifier.padding(top = 100.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(currentState.info)
                        LinearProgressIndicator(progress = { currentState.progress }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                        Text("${(currentState.progress * 100).toInt()}%")
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountItem(
    user: UserEntity,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user.face,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.name, style = MaterialTheme.typography.bodyLarge, color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
            Text(if (isCurrent) "使用中 (长按复制)" else "点击切换 / 长按复制", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, contentDescription = "注销", tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySelector(
    label: String,
    options: List<FormatOption>,
    selectedOption: FormatOption?,
    onOptionSelected: (FormatOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(
            value = selectedOption?.label ?: "无可用选项",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { onOptionSelected(option); expanded = false }
                )
            }
        }
    }
}