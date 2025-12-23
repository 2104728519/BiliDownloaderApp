package com.example.bilidownloader.features.tools.audiocrop

import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

/**
 * 音频裁剪页面.
 *
 * 提供可视化的时间轴滑块 (`RangeSlider`)，允许用户指定音频的起止时间。
 * 支持即时试听选定片段，并将结果导出为 MP3.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AudioCropScreen(
    audioUri: String,
    onBack: () -> Unit,
    viewModel: AudioCropViewModel = viewModel()
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    // 状态收集
    val totalDurationMs by viewModel.totalDuration.collectAsState()
    val isPlaying by viewModel.isPlaying.collectAsState()
    val saveState by viewModel.saveState.collectAsState()
    val currentPlayPosition by viewModel.currentPosition.collectAsState()

    // 初始化加载音频数据
    LaunchedEffect(Unit) {
        viewModel.loadAudioInfo(audioUri)
    }

    // 页面销毁时停止播放
    DisposableEffect(Unit) {
        onDispose { viewModel.stopAudio() }
    }

    // UI 内部状态
    var sliderPosition by remember { mutableStateOf(0.0f..1.0f) }
    var startTimeText by remember { mutableStateOf("00:00.000") }
    var endTimeText by remember { mutableStateOf("00:00.000") }
    var isStartFocused by remember { mutableStateOf(false) }
    var isEndFocused by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    var saveFileName by remember { mutableStateOf("") }

    // 格式化毫秒为 "MM:ss.SSS"
    fun formatMillis(ms: Long): String {
        val positiveMs = ms.coerceAtLeast(0)
        val totalSeconds = positiveMs / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        val millis = positiveMs % 1000
        return String.format(Locale.US, "%02d:%02d.%03d", minutes, seconds, millis)
    }

    // 解析 "MM:ss.SSS" 为毫秒
    fun parseTimeToMs(text: String): Long? {
        try {
            if (text.isBlank()) return null
            val parts = text.split(":")
            var minutes = 0L
            var seconds = 0.0

            when (parts.size) {
                2 -> {
                    minutes = parts[0].toLong()
                    seconds = parts[1].toDouble()
                }
                1 -> {
                    seconds = parts[0].toDouble()
                }
                else -> return null
            }
            return (minutes * 60 * 1000 + seconds * 1000).toLong()
        } catch (e: Exception) {
            return null
        }
    }

    // 状态同步：当音频总时长或滑块变动时，更新文本框 (如果未获得焦点)
    LaunchedEffect(totalDurationMs, sliderPosition) {
        if (totalDurationMs > 0) {
            val startMs = (totalDurationMs * sliderPosition.start).toLong()
            val endMs = (totalDurationMs * sliderPosition.endInclusive).toLong()
            if (!isStartFocused) {
                startTimeText = formatMillis(startMs)
            }
            if (!isEndFocused) {
                endTimeText = formatMillis(endMs)
            }
        }
    }

    // 处理保存结果的回调
    LaunchedEffect(saveState) {
        if (saveState == 2) {
            Toast.makeText(context, "保存成功！", Toast.LENGTH_SHORT).show()
            viewModel.resetSaveState()
            showDialog = false
            onBack()
        } else if (saveState == 3) {
            Toast.makeText(context, "保存失败", Toast.LENGTH_SHORT).show()
            viewModel.resetSaveState()
        }
    }

    // 保存文件弹窗
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
                    enabled = saveState != 1
                ) {
                    if (saveState == 1) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    } else {
                        Text("确定")
                    }
                }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("取消") } }
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
                    TextButton(
                        onClick = {
                            saveFileName = "Crop_${System.currentTimeMillis()}"
                            showDialog = true
                        },
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
                .pointerInput(Unit) {
                    // 点击空白处收起键盘
                    detectTapGestures(onTap = { focusManager.clearFocus() })
                }
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            // 1. 状态指示图标
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = if (totalDurationMs > 0) "准备就绪" else "加载中...",
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            // 2. 时间输入框 (支持手动修改)
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = startTimeText,
                        onValueChange = { newText ->
                            startTimeText = newText
                            val inputMs = parseTimeToMs(newText)
                            if (inputMs != null && totalDurationMs > 0) {
                                val newStartRatio = (inputMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                                if (newStartRatio < sliderPosition.endInclusive) {
                                    sliderPosition = newStartRatio..sliderPosition.endInclusive
                                }
                            }
                        },
                        label = { Text("开始 (分:秒.毫秒)") },
                        modifier = Modifier.weight(1f).onFocusChanged { isStartFocused = it.isFocused },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    OutlinedTextField(
                        value = endTimeText,
                        onValueChange = { newText ->
                            endTimeText = newText
                            val inputMs = parseTimeToMs(newText)
                            if (inputMs != null && totalDurationMs > 0) {
                                val newEndRatio = (inputMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                                if (newEndRatio > sliderPosition.start) {
                                    sliderPosition = sliderPosition.start..newEndRatio
                                }
                            }
                        },
                        label = { Text("结束 (分:秒.毫秒)") },
                        modifier = Modifier.weight(1f).onFocusChanged { isEndFocused = it.isFocused },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 3. 滑块与播放进度条
                if (totalDurationMs > 0) {
                    RangeSlider(
                        value = sliderPosition,
                        onValueChange = { range ->
                            // 限制最小间隔，防止重叠
                            if (range.endInclusive - range.start > 0.001f) {
                                sliderPosition = range
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )

                    val startMs = (totalDurationMs * sliderPosition.start).toLong()
                    val endMs = (totalDurationMs * sliderPosition.endInclusive).toLong()
                    val clipDuration = endMs - startMs

                    // 计算相对播放进度
                    val currentRelativeMs = if (isPlaying && currentPlayPosition >= startMs) {
                        currentPlayPosition - startMs
                    } else {
                        0L
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 进度条显示
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LinearProgressIndicator(
                            progress = {
                                if (clipDuration > 0) {
                                    (currentRelativeMs.toFloat() / clipDuration.toFloat()).coerceIn(0f, 1f)
                                } else 0f
                            },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${formatMillis(currentRelativeMs)} / ${formatMillis(clipDuration)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isPlaying) MaterialTheme.colorScheme.secondary else Color.Gray,
                            fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal
                        )
                    }

                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // 4. 试听按钮
            FilledTonalIconButton(
                onClick = {
                    focusManager.clearFocus()
                    viewModel.playRegion(sliderPosition.start, sliderPosition.endInclusive)
                },
                modifier = Modifier.size(64.dp),
                enabled = totalDurationMs > 0
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "停止" else "试听",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}