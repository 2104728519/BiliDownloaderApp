package com.example.bilidownloader.ui.screen

import android.widget.Toast // 【新增导入】
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
import androidx.compose.ui.platform.LocalContext // 【新增导入】
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bilidownloader.ui.viewmodel.AudioCropViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioCropScreen(
    audioUri: String,
    onBack: () -> Unit,
    viewModel: AudioCropViewModel = viewModel() // 注入车间主任
) {
    val context = LocalContext.current // 【新增】获取 Context

    // 1. 监听主任给的数据
    val totalDurationMs by viewModel.totalDuration.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val saveState by viewModel.saveState.collectAsState() // 【新增】监听保存状态

    // 2. 页面启动时，加载音频
    LaunchedEffect(Unit) {
        viewModel.loadAudioInfo(audioUri)
    }

    // 3. 滑块状态 (默认选中中间一段)
    var sliderPosition by remember { mutableStateOf(0.2f..0.8f) }

    // 【新增状态】控制弹窗显示
    var showDialog by remember { mutableStateOf(false) }
    // 【新增状态】用户输入的文件名
    var saveFileName by remember { mutableStateOf("") }

    // 【新增】监听保存结果
    LaunchedEffect(saveState) {
        when (saveState) {
            2 -> { // 成功
                Toast.makeText(context, "保存成功！", Toast.LENGTH_SHORT).show()
                viewModel.resetSaveState()
                showDialog = false
                onBack() // 保存成功后直接退出页面
            }
            3 -> { // 失败
                Toast.makeText(context, "保存失败，请重试", Toast.LENGTH_SHORT).show()
                viewModel.resetSaveState()
            }
        }
    }

    // 辅助函数：格式化时间
    fun formatTime(ratio: Float): String {
        // 如果还没加载好 (total=0)，就显示 00:00
        if (totalDurationMs == 0L) return "00:00"

        val ms = (totalDurationMs * ratio).toLong()
        val s = ms / 1000
        return String.format("%02d:%02d", s / 60, s % 60)
    }

    // === 【新增】保存文件名的弹窗 ===
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
                            // 调用 ViewModel 开始保存
                            viewModel.saveCroppedAudio(
                                saveFileName,
                                sliderPosition.start,
                                sliderPosition.endInclusive
                            )
                        } else {
                            // 防止空名字
                            Toast.makeText(context, "文件名不能为空", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = saveState != 1 // 如果正在保存中，按钮禁用
                ) {
                    if (saveState == 1) {
                        // 正在保存时显示进度圈
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
                    // 【修改】保存按钮点击事件
                    TextButton(
                        onClick = {
                            // 默认名字使用时间戳，方便识别
                            saveFileName = "Crop_${System.currentTimeMillis()}"
                            showDialog = true // 显示弹窗
                        },
                        // 只有加载完成且当前不在保存中，才能保存
                        enabled = totalDurationMs > 0 && saveState != 1
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

            // === 上部 ===
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Check, // 依然先用 Check
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

            // === 中部：滑块区 ===
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

                // 只有加载完成了才允许拖动
                if (totalDurationMs > 0) {
                    RangeSlider(
                        value = sliderPosition,
                        onValueChange = { range ->
                            // 限制：最少保留 1 秒 (0.01f 约等于 1% 的总时长)
                            if (range.endInclusive - range.start > 0.01f) {
                                sliderPosition = range
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "选中时长: ${formatTime(sliderPosition.endInclusive - sliderPosition.start)}",
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    // 加载中显示个进度条
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            // === 下部：播放控制 ===
            FilledTonalIconButton(
                onClick = {
                    // 点击播放/暂停
                    viewModel.playRegion(sliderPosition.start, sliderPosition.endInclusive)
                },
                modifier = Modifier.size(64.dp),
                enabled = totalDurationMs > 0 // 加载好之前不能点
            ) {
                Icon(
                    // 正在播放显示 X (暂停)，暂停显示三角形 (播放)
                    imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = "试听",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}