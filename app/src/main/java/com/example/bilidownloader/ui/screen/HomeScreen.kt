package com.example.bilidownloader.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi // 【新增导入】
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable // 【新增导入】
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
// 【新增】导入返回图标
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person // 导入 Person 图标
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner // 【新增】导入 LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver // 【新增】导入 LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage // 导入 Coil
import com.example.bilidownloader.data.database.HistoryEntity
import com.example.bilidownloader.data.database.UserEntity // 导入 UserEntity
import com.example.bilidownloader.ui.components.BiliWebPlayer
import com.example.bilidownloader.ui.components.HistoryItem
import com.example.bilidownloader.ui.state.FormatOption
import com.example.bilidownloader.ui.state.MainState
import com.example.bilidownloader.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel(),
    onNavigateToTranscribe: (String) -> Unit,
    onNavigateToLogin: () -> Unit // 用于“去短信登录”
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current // 【新增】获取 LifecycleOwner

    // 【监听新的状态】
    val currentUser by viewModel.currentUser.collectAsState()
    val userList by viewModel.userList.collectAsState()

    // 监听原有状态
    val state by viewModel.state.collectAsState()
    val historyList by viewModel.historyList.collectAsState()

    // 登录状态现在从 currentUser 派生，但为了兼容性，保留监听
    // val isLoggedIn by viewModel.isUserLoggedIn.collectAsState() // 移除，用 currentUser != null 代替

    var inputText by remember { mutableStateOf("") }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<HistoryEntity>() }

    // 【新增】账号管理弹窗状态
    var showAccountDialog by remember { mutableStateOf(false) }
    // 【新增】手动输入 Cookie 的二级弹窗
    var showManualCookieInput by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
    }

    // ==========================================================
    // 【核心修复 1】生命周期监听：处理短信登录后的同步
    // ==========================================================
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 当从短信登录页返回时，检查本地 Cookie 并同步到账号列表
                viewModel.syncCookieToUserDB()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // ==========================================================
    // 【核心修复 2】全局返回键逻辑
    // ==========================================================
    BackHandler(enabled = isSelectionMode || state !is MainState.Idle) {
        if (isSelectionMode) {
            // 如果在多选模式，退出多选
            isSelectionMode = false
            selectedItems.clear()
        } else if (state !is MainState.Idle) {
            // 如果在解析结果页/下载页/错误页，重置回首页
            viewModel.reset()
        }
    }

    // ==========================================================
    // 【UI】账号管理弹窗
    // ==========================================================
    if (showAccountDialog) {
        Dialog(onDismissRequest = { showAccountDialog = false }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("账号管理", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(16.dp))

                    // 1. 账号列表
                    if (userList.isEmpty()) {
                        Text("暂无账号，请添加", color = MaterialTheme.colorScheme.secondary)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(userList) { user ->
                                AccountItem(
                                    user = user,
                                    isCurrent = user.mid == currentUser?.mid,
                                    onClick = {
                                        // 如果不是当前账号，才允许切换
                                        if (user.mid != currentUser?.mid) {
                                            viewModel.switchAccount(user)
                                        }
                                    },
                                    // 【核心修改 2】实现长按复制逻辑
                                    onLongClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        // user.sessData 就是完整的 Cookie 字符串
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

                    // 2. 操作按钮区域
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        // 添加账号按钮
                        TextButton(onClick = {
                            showManualCookieInput = true
                            showAccountDialog = false // 关闭主弹窗，打开二级弹窗
                        }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("添加新账号")
                        }

                        // 退出登录状态（变回游客）
                        if (currentUser != null) {
                            TextButton(
                                onClick = {
                                    viewModel.quitToGuestMode()
                                    showAccountDialog = false
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text("退出当前 (游客)")
                            }
                        }
                    }

                    TextButton(
                        onClick = { showAccountDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("关闭")
                    }
                }
            }
        }
    }

    // ==========================================================
    // 【UI】手动输入 Cookie 弹窗 (用于添加账号)
    // ==========================================================
    if (showManualCookieInput) {
        var cookieText by remember { mutableStateOf("") }
        Dialog(onDismissRequest = { showManualCookieInput = false }) {
            Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("添加新账号", style = MaterialTheme.typography.titleMedium)
                    Text("请输入 SESSDATA 或其他完整 Cookie 串", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    OutlinedTextField(
                        value = cookieText,
                        onValueChange = { cookieText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
                        TextButton(onClick = {
                            showManualCookieInput = false
                            onNavigateToLogin() // 跳转去短信登录页面
                        }) {
                            Text("去短信登录")
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        TextButton(onClick = { showManualCookieInput = false }) { Text("取消") }
                        Button(onClick = {
                            // 【核心修复 3】手动输入必须调用 addOrUpdateAccount 才能存入数据库
                            viewModel.addOrUpdateAccount(cookieText)
                            showManualCookieInput = false
                            // 打开主弹窗，以便用户看到结果
                            showAccountDialog = true
                        }, enabled = cookieText.isNotBlank()) {
                            Text("确认添加/切换")
                        }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    if (isSelectionMode) {
                        Text("已选 ${selectedItems.size} 项")
                    } else {
                        Text("B 站视频下载器")
                    }
                },
                // 【核心修复 4】在非首页状态显示左上角返回按钮
                navigationIcon = {
                    if (state !is MainState.Idle && !isSelectionMode) {
                        IconButton(onClick = { viewModel.reset() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = if (isSelectionMode) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                ),
                actions = {
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            viewModel.deleteHistories(selectedItems.toList())
                            exitSelectionMode()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    } else {
                        // 【替换】头像/菜单入口
                        IconButton(onClick = { showAccountDialog = true }) {
                            // 如果已登录显示头像，否则显示默认图标
                            if (currentUser != null) {
                                // 使用 AsyncImage 显示头像
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
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state is MainState.Idle && !isSelectionMode) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    label = { Text("粘贴 B 站链接或文字") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        onClick = { inputText = "" },
                        enabled = inputText.isNotEmpty()
                    ) {
                        Text("清空输入")
                    }
                }

                Button(
                    onClick = { viewModel.analyzeInput(inputText) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = inputText.isNotBlank()
                ) {
                    Text("解析链接")
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (historyList.isNotEmpty()) {
                    Text(
                        text = "历史记录",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            when (val currentState = state) {
                is MainState.Idle -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
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
                                        inputText = "https://www.bilibili.com/video/${history.bvid}"
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
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在解析链接...")
                        }
                    }
                }

                is MainState.ChoiceSelect -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BiliWebPlayer(bvid = currentState.detail.bvid)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = currentState.detail.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Text(
                            text = "UP主: ${currentState.detail.owner.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        OutlinedCard(
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("下载选项", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(16.dp))
                                QualitySelector(
                                    label = "视频画质",
                                    options = currentState.videoFormats,
                                    // 直接从 ChoiceSelect 状态中读取 selectedVideo
                                    selectedOption = currentState.selectedVideo,
                                    onOptionSelected = { viewModel.updateSelectedVideo(it) }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                QualitySelector(
                                    label = "音频音质",
                                    options = currentState.audioFormats,
                                    // 直接从 ChoiceSelect 状态中读取 selectedAudio
                                    selectedOption = currentState.selectedAudio,
                                    onOptionSelected = { viewModel.updateSelectedAudio(it) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                        Button(
                            onClick = { viewModel.startDownload(audioOnly = false) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("下载视频 (MP4)")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.startDownload(audioOnly = true) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("仅下载音频")
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = {
                                viewModel.prepareForTranscription { path ->
                                    onNavigateToTranscribe(path)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI 音频转文字")
                        }
                        Text(
                            text = "转写将自动使用最高音质以确保准确率",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                is MainState.Processing -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = currentState.info)
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(progress = { currentState.progress }, modifier = Modifier.fillMaxWidth())
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = "${(currentState.progress * 100).toInt()}%")
                        }
                    }
                }
                is MainState.Success -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "🎉 ${currentState.message}", color = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = {
                                inputText = ""
                                viewModel.reset()
                            }) { Text("返回") }
                        }
                    }
                }
                is MainState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(text = "❌ ${currentState.errorMsg}", color = MaterialTheme.colorScheme.error)
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(onClick = { viewModel.reset() }) { Text("返回修改") }
                        }
                    }
                }
            }
        }
    }
}

// 辅助组件：账号列表项
@OptIn(ExperimentalFoundationApi::class) // 【核心修改 1】新增注解
@Composable
fun AccountItem(
    user: UserEntity,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit, // 【核心修改 1】新增长按回调
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 【核心修改 1】使用 combinedClickable 支持点击和长按
            .combinedClickable(
                onClick = {
                    // 如果不是当前账号，才允许切换
                    if (!isCurrent) {
                        onClick()
                    }
                },
                onLongClick = {
                    onLongClick() // 触发长按回调
                }
            )
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
            Text(
                user.name,
                style = MaterialTheme.typography.bodyLarge,
                // 如果是当前用户，加粗或颜色突出
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            if (isCurrent) {
                // 【核心修改 1】修改提示文案
                Text("当前使用中 (长按复制 Cookie)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            } else {
                // 【核心修改 1】修改提示文案
                Text("点击切换 / 长按复制 Cookie", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
            }
        }
        // 删除按钮
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Close, contentDescription = "注销", tint = MaterialTheme.colorScheme.outline)
        }
    }
}

// (QualitySelector 组件保持不变)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QualitySelector(
    label: String,
    options: List<FormatOption>,
    selectedOption: FormatOption?,
    onOptionSelected: (FormatOption) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    if (options.isEmpty()){
        OutlinedTextField(
            value = "无可用选项",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            enabled = false
        )
        return
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        OutlinedTextField(
            value = selectedOption?.label ?: "请选择...",
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(option.label, style = MaterialTheme.typography.bodyLarge)
                            if (option.codecs != null) {
                                Text(
                                    "编码: ${option.codecs}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }
                    },
                    onClick = {
                        onOptionSelected(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                )
            }
        }
    }
}