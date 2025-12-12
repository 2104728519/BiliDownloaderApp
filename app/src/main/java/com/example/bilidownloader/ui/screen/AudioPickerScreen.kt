package com.example.bilidownloader.ui.screen

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bilidownloader.ui.viewmodel.AudioPickerViewModel
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AudioPickerScreen(
    viewModel: AudioPickerViewModel = viewModel(),
    onBack: () -> Unit, // 【新增】接收返回回调
    onAudioSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val audioList by viewModel.audioList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // 监听正序/倒序状态
    val isAscending by viewModel.isAscending.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    // --- 权限与 Launcher 代码保持不变 ---
    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            Toast.makeText(context, "权限已授予，正在重试...", Toast.LENGTH_SHORT).show()
            viewModel.onPermissionGranted()
        } else {
            Toast.makeText(context, "操作已取消", Toast.LENGTH_SHORT).show()
        }
    }

    LaunchedEffect(viewModel.pendingPermissionIntent) {
        viewModel.pendingPermissionIntent?.let { intentSender ->
            intentSenderLauncher.launch(IntentSenderRequest.Builder(intentSender).build())
            viewModel.permissionRequestHandled()
        }
    }

    val systemPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) onAudioSelected(uri)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadAudios()
        } else {
            Toast.makeText(context, "需要权限才能扫描音频", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }

    // --- 列表状态 ---
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = viewModel.scrollIndex,
        initialFirstVisibleItemScrollOffset = viewModel.scrollOffset
    )
    DisposableEffect(Unit) {
        onDispose {
            viewModel.scrollIndex = listState.firstVisibleItemIndex
            viewModel.scrollOffset = listState.firstVisibleItemScrollOffset
        }
    }

    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            if (isSearchActive) {
                TopAppBar(
                    title = {
                        TextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                viewModel.searchAudio(it)
                            },
                            placeholder = { Text("搜索音频...") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            ),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth()
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            isSearchActive = false
                            searchQuery = ""
                            viewModel.searchAudio("")
                            focusManager.clearFocus()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "退出搜索")
                        }
                    },
                    actions = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = {
                                searchQuery = ""
                                viewModel.searchAudio("")
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "清空")
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                CenterAlignedTopAppBar(
                    title = { Text("选择音频") },
                    // 【新增】返回按钮
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    actions = {
                        IconButton(onClick = { isSearchActive = true }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "排序")
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            DropdownMenuItem(text = { Text("按时间") }, onClick = { viewModel.changeSortType(AudioPickerViewModel.SortType.DATE); showSortMenu = false })
                            DropdownMenuItem(text = { Text("按大小") }, onClick = { viewModel.changeSortType(AudioPickerViewModel.SortType.SIZE); showSortMenu = false })
                            DropdownMenuItem(text = { Text("按时长") }, onClick = { viewModel.changeSortType(AudioPickerViewModel.SortType.DURATION); showSortMenu = false })

                            Divider()

                            // 正序/倒序切换选项
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(if (isAscending) "当前：正序 (A→Z)" else "当前：倒序 (Z→A)")
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            if (isAscending) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    viewModel.toggleSortOrder()
                                    showSortMenu = false
                                }
                            )
                        }
                    }
                )
            }
        },
        floatingActionButton = {
            if (!isSearchActive) {
                ExtendedFloatingActionButton(
                    onClick = { systemPickerLauncher.launch("audio/*") },
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("浏览文件夹") }
                )
            }
        }
    ) { paddingValues ->

        // 使用 Box 包裹 LazyColumn 以便叠加滚动条
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (audioList.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (isSearchActive) Icons.Default.SearchOff else Icons.Default.AudioFile,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isSearchActive) "未找到相关音频" else "没有找到音频文件",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            } else {
                // 原有的 List
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                    items(audioList, key = { it.id }) { audio ->
                        var isMenuExpanded by remember { mutableStateOf(false) }
                        Box {
                            ListItem(
                                headlineContent = { Text(audio.title, maxLines = 1, fontWeight = FontWeight.Bold) },
                                supportingContent = {
                                    Column {
                                        Text("${audio.durationText} | ${audio.sizeText}", style = MaterialTheme.typography.bodySmall)
                                        Text(audio.dateText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
                                    }
                                },
                                leadingContent = { Icon(Icons.Default.Audiotrack, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.combinedClickable(
                                    onClick = { onAudioSelected(audio.uri) },
                                    onLongClick = {
                                        viewModel.selectedAudioForAction = audio
                                        isMenuExpanded = true
                                    }
                                )
                            )

                            // 菜单保持不变...
                            DropdownMenu(
                                expanded = isMenuExpanded,
                                onDismissRequest = { isMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("分享") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                    onClick = { isMenuExpanded = false; viewModel.shareAudio(context, audio) }
                                )
                                Divider()
                                DropdownMenuItem(
                                    text = { Text("重命名") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) },
                                    onClick = { isMenuExpanded = false; viewModel.selectedAudioForAction = audio; viewModel.showRenameDialog = true }
                                )
                                DropdownMenuItem(
                                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) },
                                    onClick = { isMenuExpanded = false; viewModel.selectedAudioForAction = audio; viewModel.showDeleteDialog = true }
                                )
                            }
                        }
                        Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }

                // 快速滚动条
                if (audioList.size > 10) {
                    FastScrollbar(
                        listState = listState,
                        itemCount = audioList.size,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(24.dp)
                    )
                }
            }
        }

        // --- 弹窗组件代码保持不变 ---
        if (viewModel.showDeleteDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.showDeleteDialog = false },
                title = { Text("确认删除") },
                text = { Text("确定要删除文件 \"${viewModel.selectedAudioForAction?.title}\" 吗？此操作无法撤销。") },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.deleteSelectedAudio() },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text("删除") }
                },
                dismissButton = { TextButton(onClick = { viewModel.showDeleteDialog = false }) { Text("取消") } }
            )
        }

        if (viewModel.showRenameDialog) {
            val initialName = viewModel.selectedAudioForAction?.title?.substringBeforeLast(".") ?: ""
            var newNameInput by remember { mutableStateOf(initialName) }
            AlertDialog(
                onDismissRequest = { viewModel.showRenameDialog = false },
                title = { Text("重命名") },
                text = {
                    OutlinedTextField(
                        value = newNameInput,
                        onValueChange = { newNameInput = it },
                        label = { Text("新文件名 (无需后缀)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = { if(newNameInput.isNotBlank()) viewModel.renameSelectedAudio(newNameInput) },
                        enabled = newNameInput.isNotBlank()
                    ) { Text("确定") }
                },
                dismissButton = { TextButton(onClick = { viewModel.showRenameDialog = false }) { Text("取消") } }
            )
        }
    }
}

// 快速滚动条组件 (保持不变，但需包含在文件内)
@Composable
fun FastScrollbar(
    listState: LazyListState,
    itemCount: Int,
    modifier: Modifier = Modifier,
    thumbColor: Color = MaterialTheme.colorScheme.primary
) {
    val scope = rememberCoroutineScope()
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(0f) }

    BoxWithConstraints(modifier = modifier) {
        val maxHeight = constraints.maxHeight.toFloat()
        // 滚动条滑块的高度，根据列表长度动态调整，但不小于 40f
        val thumbHeight = max(40f, maxHeight / max(1, itemCount) * 2)

        // 计算当前滑块位置
        val thumbOffset = if (isDragging) {
            dragOffset
        } else {
            val firstVisible = listState.firstVisibleItemIndex.toFloat()
            (firstVisible / max(1, itemCount)) * (maxHeight - thumbHeight)
        }

        // 绘制滑块
        Box(
            modifier = Modifier
                .offset(y = (thumbOffset / LocalContext.current.resources.displayMetrics.density).dp)
                .size(width = 6.dp, height = (thumbHeight / LocalContext.current.resources.displayMetrics.density).dp)
                .align(Alignment.TopCenter)
                .clip(RoundedCornerShape(4.dp))
                .background(if (isDragging) thumbColor else thumbColor.copy(alpha = 0.5f))
        )

        // 透明的触摸响应区域
        Box(
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        isDragging = true
                        dragOffset = (dragOffset + delta).coerceIn(0f, maxHeight - thumbHeight)
                        val progress = dragOffset / (maxHeight - thumbHeight)
                        val targetIndex = (progress * itemCount).toInt().coerceIn(0, itemCount - 1)
                        scope.launch {
                            listState.scrollToItem(targetIndex)
                        }
                    },
                    onDragStopped = { isDragging = false },
                    onDragStarted = { offset ->
                        isDragging = true
                        val newOffset = (offset.y - thumbHeight / 2).coerceIn(0f, maxHeight - thumbHeight)
                        dragOffset = newOffset
                        val progress = dragOffset / (maxHeight - thumbHeight)
                        val targetIndex = (progress * itemCount).toInt().coerceIn(0, itemCount - 1)
                        scope.launch { listState.scrollToItem(targetIndex) }
                    }
                )
        )
    }
}