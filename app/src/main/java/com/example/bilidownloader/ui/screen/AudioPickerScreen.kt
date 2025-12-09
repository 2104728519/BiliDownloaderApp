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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
    val focusManager = LocalFocusManager.current
    val audioList by viewModel.audioList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var isSearchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

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

        Box(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
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

                            // ★★★【修改】DropdownMenu ★★★
                            DropdownMenu(
                                expanded = isMenuExpanded,
                                onDismissRequest = { isMenuExpanded = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("分享") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "分享",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        isMenuExpanded = false
                                        viewModel.shareAudio(context, audio)
                                    }
                                )

                                Divider() // 使用 Material 3 的 Divider

                                DropdownMenuItem(
                                    text = { Text("重命名") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "重命名",
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        isMenuExpanded = false
                                        viewModel.selectedAudioForAction = audio
                                        viewModel.showRenameDialog = true
                                    }
                                )

                                DropdownMenuItem(
                                    text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "删除",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    },
                                    onClick = {
                                        isMenuExpanded = false
                                        viewModel.selectedAudioForAction = audio
                                        viewModel.showDeleteDialog = true
                                    }
                                )
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