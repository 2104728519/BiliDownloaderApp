package com.example.bilidownloader.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bilidownloader.ui.viewmodel.AudioCropViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioCropScreen(
    audioUri: String,
    onBack: () -> Unit,
    viewModel: AudioCropViewModel = viewModel() // 注入 ViewModel
) {
    val context = LocalContext.current

    // 1. 从 ViewModel 收集状态
    val totalDurationMs by viewModel.totalDuration.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val saveState by viewModel.saveState.collectAsState()

    // 2. 页面首次加载时，请求 ViewModel 加载音频信息
    LaunchedEffect(Unit) {
        viewModel.loadAudioInfo(audioUri)
    }

    // 【新增】监听页面销毁事件
    // 当用户点击返回键，或者从底部导航栏切换到其他页面时，这个 Composable 会被销毁，
    // onDispose 代码块会被触发。
    DisposableEffect(Unit) {
        onDispose {
            // 确保在离开页面时，强制停止任何正在播放的音频，以防止内存泄漏和崩溃。
            viewModel.stopAudio()
        }
    }

    // 3. UI 内部状态
    var sliderPosition by remember { mutableStateOf(0.2f..0.8f) }
    var showDialog by remember { mutableStateOf(false) }
    var saveFileName by remember { mutableStateOf("") }

    // 4. 监听保存状态的变化，并给出用户反馈
    LaunchedEffect(saveState) {
        when (saveState) {
            2 -> { // 成功
                Toast.makeText(context, "保存成功！", Toast.LENGTH_SHORT).show()
                viewModel.resetSaveState()
                showDialog = false
                onBack() // 保存成功后自动返回上一页
            }
            3 -> { // 失败
                Toast.makeText(context, "保存失败，请重试", Toast.LENGTH_SHORT).show()
                viewModel.resetSaveState()
            }
        }
    }

    // 辅助函数：将时长（毫秒）格式化为 "mm:ss" 字符串
    fun formatTime(ratio: Float): String {
        if (totalDurationMs == 0L) return "00:00"
        val ms = (totalDurationMs * ratio).toLong()
        val totalSeconds = ms / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format("%02d:%02d", minutes, seconds)
    }

    // === 【UI】保存文件名的弹窗 ===
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("保存音频") },
            text = {
                Column {
                    Text("给裁剪后的音频起个名字：")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = saveFileName,
                        onValueChange = { saveFileName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如：我的铃声") }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (saveFileName.isNotBlank()) {
                            viewModel.saveCroppedAudio(
                                saveFileName,
                                sliderPosition.start,
                                sliderPosition.endInclusive
                            )
                        } else {
                            Toast.makeText(context, "文件名不能为空", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = saveState != 1 // 正在保存时禁用按钮
                ) {
                    if (saveState == 1) { // 正在保存时显示加载动画
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("确定")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }


    // === 【UI】主界面布局 ===
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("裁剪音频") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            saveFileName = "Crop_${System.currentTimeMillis()}"
                            showDialog = true
                        },
                        enabled = totalDurationMs > 0 && saveState != 1 // 加载完成且不在保存中才可点击
                    ) {
                        Text("保存", fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // 上部：图标和状态
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(100.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = if (totalDurationMs > 0) "准备就绪" else "加载中...",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            // 中部：滑块和时间显示
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("开始", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = formatTime(sliderPosition.start),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("结束", style = MaterialTheme.typography.labelMedium)
                        Text(
                            text = formatTime(sliderPosition.endInclusive),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (totalDurationMs > 0) {
                    RangeSlider(
                        value = sliderPosition,
                        onValueChange = { range ->
                            sliderPosition = range
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        text = "选中时长: ${formatTime(sliderPosition.endInclusive - sliderPosition.start)}",
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            // 下部：播放控制按钮
            FilledTonalIconButton(
                onClick = {
                    viewModel.playRegion(sliderPosition.start, sliderPosition.endInclusive)
                },
                modifier = Modifier.size(64.dp),
                enabled = totalDurationMs > 0 // 加载完成才能播放
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "停止试听" else "试听",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}