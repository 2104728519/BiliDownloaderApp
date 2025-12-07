package com.example.bilidownloader.ui.screen

import android.Manifest
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bilidownloader.ui.viewmodel.AudioPickerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioPickerScreen(
    viewModel: AudioPickerViewModel = viewModel(),
    onAudioSelected: (Uri) -> Unit // 回调：当用户选好了音频，把地址传出去
) {
    val context = LocalContext.current
    val audioList by viewModel.audioList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    // 1. 准备“手动选择文件”的启动器
    val systemPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            onAudioSelected(uri) // 如果用户手动选了，直接回调
        }
    }

    // 2. 准备“权限申请”的启动器
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.loadAudios() // 假如给了权限，立马加载
        } else {
            Toast.makeText(context, "需要权限才能扫描音频，请尝试右下角手动选择", Toast.LENGTH_LONG).show()
        }
    }

    // 3. 页面一进入，自动检查并申请权限
    LaunchedEffect(Unit) {
        // 根据安卓版本判断申请哪个权限
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        permissionLauncher.launch(permission)
    }

    // 控制排序菜单的显示
    var showSortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("选择音频") },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                actions = {
                    // 排序按钮
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(Icons.Default.List, contentDescription = "排序")
                    }
                    // 排序下拉菜单
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("按时间 (最新)") },
                            onClick = {
                                viewModel.changeSortType(AudioPickerViewModel.SortType.DATE)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("按大小 (最大)") },
                            onClick = {
                                viewModel.changeSortType(AudioPickerViewModel.SortType.SIZE)
                                showSortMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("按时长 (最长)") },
                            onClick = {
                                viewModel.changeSortType(AudioPickerViewModel.SortType.DURATION)
                                showSortMenu = false
                            }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            // 右下角悬浮按钮：手动浏览文件夹
            ExtendedFloatingActionButton(
                onClick = { systemPickerLauncher.launch("audio/*") }, // 只选音频
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
                    text = "没有找到音频文件\n试试右下角手动选择",
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.Gray
                )
            } else {
                // 音频列表
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(audioList) { audio ->
                        // 单个音频条目
                        ListItem(
                            headlineContent = {
                                Text(audio.title, maxLines = 1, fontWeight = FontWeight.Bold)
                            },
                            // 【修改】这里改成显示两行信息
                            supportingContent = {
                                Column {
                                    // 第一行：时长 | 大小
                                    Text(
                                        text = "${audio.durationText} | ${audio.sizeText}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    // 第二行：年月日 时:分 (灰色小字)
                                    Text(
                                        text = audio.dateText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color.Gray
                                    )
                                }
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.Menu, // 暂时用个菜单图标代替音符
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.clickable {
                                onAudioSelected(audio.uri) // 点击选中
                            }
                        )
                        Divider(thickness = 0.5.dp, color = Color.LightGray.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }
}