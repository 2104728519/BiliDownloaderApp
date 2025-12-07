package com.example.bilidownloader.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bilidownloader.data.database.HistoryEntity
import com.example.bilidownloader.ui.components.BiliWebPlayer
import com.example.bilidownloader.ui.components.HistoryItem
import com.example.bilidownloader.ui.state.MainState
import com.example.bilidownloader.ui.viewmodel.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: MainViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    // 监听数据库里的历史记录
    val historyList by viewModel.historyList.collectAsState()

    var inputText by remember { mutableStateOf("") }

    // === 多选模式的状态管理 ===
    // 是否处于选择模式
    var isSelectionMode by remember { mutableStateOf(false) }
    // 已经选中的条目
    val selectedItems = remember { mutableStateListOf<HistoryEntity>() }

    // 辅助函数：退出选择模式
    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
    }

    // 监听返回键：如果在多选模式，按返回键是退出模式，而不是退出 APP
    BackHandler(enabled = isSelectionMode) {
        exitSelectionMode()
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    // 标题栏动态变化：多选模式下显示“已选 X 项”
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
                    // 如果在多选模式，右上角显示删除按钮
                    if (isSelectionMode) {
                        IconButton(onClick = {
                            // 1. 告诉 ViewModel 删除这些
                            viewModel.deleteHistories(selectedItems.toList())
                            // 2. 退出模式
                            exitSelectionMode()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->

        // 整个页面的容器
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 只有在空闲状态 (Idle) 且不在多选模式下，才显示输入框
            // 这样界面更清爽，多选时专注于管理
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

                // 历史记录标题
                if (historyList.isNotEmpty()) {
                    Text(
                        text = "历史记录",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // 根据状态显示内容
            when (val currentState = state) {

                // 1. 空闲状态：显示历史记录列表
                is MainState.Idle -> {
                    // LazyColumn 就像 RecyclerView，专门用来显示长列表
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f), // 占满剩下的空间
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
                                        // 多选模式：点击 = 选中/取消选中
                                        if (selectedItems.contains(history)) {
                                            selectedItems.remove(history)
                                            // 如果都没选中了，自动退出模式
                                            if (selectedItems.isEmpty()) isSelectionMode = false
                                        } else {
                                            selectedItems.add(history)
                                        }
                                    } else {
                                        // 正常模式：点击 = 解析这个视频
                                        // 直接把 BV 号填入输入框并解析
                                        inputText = "https://www.bilibili.com/video/${history.bvid}"
                                        viewModel.analyzeInput(history.bvid)
                                    }
                                },
                                onLongClick = {
                                    // 长按：进入多选模式，并选中当前项
                                    if (!isSelectionMode) {
                                        isSelectionMode = true
                                        selectedItems.add(history)
                                    }
                                }
                            )
                        }
                    }
                }

                // 2. 解析中：转圈圈
                is MainState.Analyzing -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("正在解析链接...")
                        }
                    }
                }

                // 3. 选择状态：找到了！让用户选
                is MainState.ChoiceSelect -> {
                    BiliWebPlayer(bvid = currentState.detail.bvid)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(currentState.detail.title, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("UP主: ${currentState.detail.owner.name}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(32.dp))
                    Button(onClick = { viewModel.startDownload(false) }, modifier = Modifier.fillMaxWidth()) { Text("下载视频 (MP4)") }
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(onClick = { viewModel.startDownload(true) }, modifier = Modifier.fillMaxWidth()) { Text("仅下载音频 (MP3)") }
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = { viewModel.reset() }, modifier = Modifier.fillMaxWidth()) { Text("取消") }
                }

                // 4. 干活中：显示进度条
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

                // 5. 成功：放烟花
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

                // 6. 失败：显示错误
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