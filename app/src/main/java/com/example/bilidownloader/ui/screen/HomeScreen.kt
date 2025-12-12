package com.example.bilidownloader.ui.screen

import android.app.Application
import android.widget.Toast // 导入 Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner // 【新增导入】生命周期所有者
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle // 【新增导入】Lifecycle
import androidx.lifecycle.LifecycleEventObserver // 【新增导入】LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bilidownloader.data.database.HistoryEntity
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
    onNavigateToLogin: () -> Unit // <--- 跳转到登录页的入口
) {
    val state by viewModel.state.collectAsState()
    val historyList by viewModel.historyList.collectAsState()
    // 【修改】收集响应式的登录状态
    val isLoggedIn by viewModel.isUserLoggedIn.collectAsState()

    val context = LocalContext.current // 获取 Context 用于 Toast

    // ==========================================================
    // 【核心修复】添加生命周期监听
    // 当页面从 "登录页" 返回到 "首页" 时 (ON_RESUME)，强制刷新登录状态
    // ==========================================================
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                // 每次页面可见时，检查 Cookie 是否存在 (即检查登录状态)
                viewModel.checkLoginStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
    // ==========================================================

    var inputText by remember { mutableStateOf("") }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<HistoryEntity>() }

    // 【新增】控制 Cookie 设置对话框的显示状态
    var showCookieDialog by remember { mutableStateOf(false) }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
    }

    BackHandler(enabled = isSelectionMode) {
        exitSelectionMode()
    }

    // 【新增】如果 showCookieDialog 为 true，则显示对话框
    if (showCookieDialog) {
        CookieSetupDialog(
            currentCookie = viewModel.getCurrentCookieValue(),
            onDismiss = { showCookieDialog = false },
            onSave = { newCookie ->
                viewModel.saveCookie(newCookie)
                showCookieDialog = false
                Toast.makeText(context, "Cookie 已保存", Toast.LENGTH_SHORT).show()
            }
        )
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
                        // 【修改】右上角菜单：根据 isLoggedIn 状态显示不同选项
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多选项")
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false }
                            ) {
                                // 【逻辑分支：已登录】 (使用 isLoggedIn)
                                if (isLoggedIn) {
                                    DropdownMenuItem(
                                        text = { Text("退出登录") },
                                        onClick = {
                                            menuExpanded = false
                                            viewModel.logout() // 调用 ViewModel 中的退出登录逻辑
                                            Toast.makeText(
                                                context,
                                                "已退出登录",
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    )
                                } else {
                                    // 【逻辑分支：未登录】 (使用 isLoggedIn)
                                    DropdownMenuItem(
                                        text = { Text("短信验证码登录") },
                                        onClick = {
                                            menuExpanded = false
                                            onNavigateToLogin()
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("手动设置 Cookie (SESSDATA)") },
                                        onClick = {
                                            menuExpanded = false
                                            showCookieDialog = true
                                        }
                                    )
                                }
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
                                    // 【核心修改】直接从 ChoiceSelect 状态中读取 selectedVideo
                                    selectedOption = currentState.selectedVideo,
                                    onOptionSelected = { viewModel.updateSelectedVideo(it) }
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                QualitySelector(
                                    label = "音频音质",
                                    options = currentState.audioFormats,
                                    // 【核心修改】直接从 ChoiceSelect 状态中读取 selectedAudio
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
                            Text("仅下载音频 (MP3)")
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
                        Spacer(modifier = Modifier.height(16.dp))
                        TextButton(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("取消")
                        }
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

// 【新增组件】Cookie 设置对话框
@Composable
fun CookieSetupDialog(
    currentCookie: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var text by remember { mutableStateOf(currentCookie) }
    // 确保导入 android.widget.Toast
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = MaterialTheme.shapes.large,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "设置 Cookie",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "请从浏览器 F12 获取并粘贴您的 SESSDATA 值。格式为 “SESSDATA=xxxx” 或直接粘贴 “xxxx” 部分均可。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("SESSDATA") },
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("取消")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        onSave(text)
                        // Toast 提示已移到 HomeScreen 中
                    }) {
                        Text("保存")
                    }
                }
            }
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