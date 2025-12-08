package com.example.bilidownloader.ui.screen

import android.Manifest
import android.app.Activity
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bilidownloader.ui.viewmodel.AudioPickerViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AudioPickerScreen(
    viewModel: AudioPickerViewModel = viewModel(),
    onAudioSelected: (Uri) -> Unit
) {
    val context = LocalContext.current
    val audioList by viewModel.audioList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // --- 【新增】系统权限弹窗启动器 ---
    val intentSenderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 用户点击了“允许”，通知 ViewModel 重试刚刚的操作
            Toast.makeText(context, "权限已授予，正在重试...", Toast.LENGTH_SHORT).show()
            viewModel.onPermissionGranted()
        } else {
            // 用户取消了授权
            Toast.makeText(context, "操作已取消", Toast.LENGTH_SHORT).show()
        }
    }

    // --- 【新增】监听 ViewModel 是否需要弹窗 ---
    LaunchedEffect(viewModel.pendingPermissionIntent) {
        viewModel.pendingPermissionIntent?.let { intentSender ->
            // 启动系统弹窗
            intentSenderLauncher.launch(
                IntentSenderRequest.Builder(intentSender).build()
            )
            // 通知 ViewModel Intent 已经被处理，防止重组时重复启动
            viewModel.permissionRequestHandled()
        }
    }

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

    val systemPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) onAudioSelected(uri)
    }

    // 媒体文件读取权限启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadAudios()
        } else {
            Toast.makeText(context, "需要权限才能扫描音频", Toast.LENGTH_LONG).show()
        }
    }

    // 页面首次进入时申请权限
    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }

    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("选择音频") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                actions = {
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
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { systemPickerLauncher.launch("audio/*") },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("浏览文件夹") }
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (audioList.isEmpty()) {
                Text(
                    text = "没有找到音频文件",
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            } else {
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
                            DropdownMenu(expanded = isMenuExpanded, onDismissRequest = { isMenuExpanded = false }) {
                                DropdownMenuItem(text = { Text("重命名") }, onClick = { isMenuExpanded = false; viewModel.showRenameDialog = true })
                                DropdownMenuItem(text = { Text("删除", color = MaterialTheme.colorScheme.error) }, onClick = { isMenuExpanded = false; viewModel.showDeleteDialog = true })
                            }
                        }
                        Divider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            }
        }

        // --- 弹窗组件 ---
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