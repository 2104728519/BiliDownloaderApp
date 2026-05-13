package com.example.bilidownloader.features.home.components

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.bilidownloader.features.home.HomeState
import com.example.bilidownloader.features.home.HomeViewModel

@Composable
fun SubtitleDialog(
    currentState: HomeState.ChoiceSelect,
    viewModel: HomeViewModel,
    onDismiss: () -> Unit,
    onNavigateToTranscribe: (String, String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    if (currentState.subtitleContent.startsWith("ERROR:")) {
        val errorMsg = currentState.subtitleContent.removePrefix("ERROR:")
        LaunchedEffect(errorMsg) {
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG)
                .show(); viewModel.consumeSubtitleError()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxSize()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (currentState.subtitleData != null) IconButton(onClick = { viewModel.clearSubtitleState() }) {
                            Icon(Icons.Default.ArrowBack, "重选")
                        }
                        else {
                            Icon(
                                Icons.Default.AutoAwesome,
                                null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(
                            if (currentState.subtitleData != null) "字幕详情" else "AI 摘要 & 字幕",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "关闭") }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (currentState.isSubtitleLoading) CircularProgressIndicator()
                    else if (currentState.subtitleData == null) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Default.Subtitles,
                                null,
                                Modifier.size(64.dp),
                                MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "获取视频的 AI 总结与字幕",
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { viewModel.fetchSubtitle() }) { Text("立即获取 (B站 API)") }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "或者",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                            Spacer(Modifier.height(16.dp))

                            OutlinedButton(onClick = {
                                val savedTitle = currentState.detail.title
                                viewModel.prepareForTranscription { path ->
                                    onNavigateToTranscribe(
                                        path,
                                        savedTitle
                                    )
                                }
                            }) { Text("没有字幕？试试阿里云转写") }
                        }
                    } else {
                        OutlinedTextField(
                            value = currentState.subtitleContent,
                            onValueChange = { viewModel.updateSubtitleContent(it) },
                            modifier = Modifier.fillMaxSize(),
                            textStyle = MaterialTheme.typography.bodyMedium.copy(lineHeight = MaterialTheme.typography.bodyMedium.fontSize * 1.5),
                            label = { Text("预览与编辑") }
                        )
                    }
                }
                if (currentState.subtitleData != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = currentState.isTimestampEnabled,
                                onCheckedChange = { viewModel.toggleTimestamp(it) },
                                modifier = Modifier.graphicsLayer(scaleX = 0.8f, scaleY = 0.8f)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("时间轴", style = MaterialTheme.typography.bodyMedium)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.exportSubtitle(currentState.subtitleContent) }) {
                                Icon(
                                    Icons.Default.SaveAlt,
                                    "导出为TXT",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                clipboardManager.setText(AnnotatedString(currentState.subtitleContent));
                                Toast.makeText(context, "内容已复制", Toast.LENGTH_SHORT).show();
                                onDismiss()
                            }) {
                                Icon(Icons.Default.ContentCopy, null, Modifier.size(16.dp)); Spacer(
                                Modifier.width(8.dp)
                            ); Text("复制并关闭")
                            }
                        }
                    }
                }
            }
        }
    }
}
