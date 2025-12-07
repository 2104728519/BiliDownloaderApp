package com.example.bilidownloader.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState // 【导入】
import androidx.compose.foundation.verticalScroll // 【导入】
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
    viewModel: MainViewModel = viewModel(),
    onNavigateToTranscribe: (String) -> Unit // 回调：要去转写页了，带上路径
) {
    val state by viewModel.state.collectAsState()
    val historyList by viewModel.historyList.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var isSelectionMode by remember { mutableStateOf(false) }
    val selectedItems = remember { mutableStateListOf<HistoryEntity>() }

    fun exitSelectionMode() {
        isSelectionMode = false
        selectedItems.clear()
    }

    BackHandler(enabled = isSelectionMode) {
        exitSelectionMode()
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

                // 3. 选择状态
                is MainState.ChoiceSelect -> {

                    // 【关键修改】加一层可滚动的 Column
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState()), // 允许垂直滚动
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        // --- 原来的内容放里面 ---

                        // 1. 播放器
                        com.example.bilidownloader.ui.components.BiliWebPlayer(bvid = currentState.detail.bvid)

                        Spacer(modifier = Modifier.height(16.dp))

                        // 2. 标题和作者
                        Text(
                            text = currentState.detail.title,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "UP主: ${currentState.detail.owner.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )

                        Spacer(modifier = Modifier.height(32.dp))

                        // 3. 各种按钮
                        Button(
                            onClick = { viewModel.startDownload(audioOnly = false) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("下载视频 (MP4)")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { viewModel.startDownload(audioOnly = true) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("仅下载音频 (MP3)")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 新增的转写按钮
                        Button(
                            onClick = {
                                viewModel.prepareForTranscription { path ->
                                    onNavigateToTranscribe(path)
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Icon(
                                imageVector = androidx.compose.material.icons.Icons.Default.Edit,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("AI 音频转文字")
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 4. 取消按钮 (现在可以滑到底部看见它了)
                        TextButton(
                            onClick = { viewModel.reset() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("取消")
                        }

                        // 底部留点白，防止贴边
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