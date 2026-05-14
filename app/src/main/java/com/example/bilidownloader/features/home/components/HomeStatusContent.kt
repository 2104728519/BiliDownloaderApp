package com.example.bilidownloader.features.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.example.bilidownloader.features.home.HomeState
import com.example.bilidownloader.features.home.HomeViewModel

@Composable
fun HomeProcessingContent(
    state: HomeState.Processing,
    viewModel: HomeViewModel
) {
    Column(
        modifier = Modifier
            .padding(top = 60.dp)
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(state.info, style = MaterialTheme.typography.titleMedium)
        if (!state.isMerging) {
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(8.dp),
                strokeCap = StrokeCap.Round
            )
            state.detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(
                "${(state.progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge
            )
        } else {
            Spacer(modifier = Modifier.height(20.dp))
            val mergeText =
                if (state.info.contains("裁剪")) "正在裁剪片段..." else if (state.info.contains("音频")) "正在处理音频格式..." else "音视频封装合并中..."
            Text(mergeText, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { state.mergeProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(8.dp),
                color = MaterialTheme.colorScheme.tertiary,
                strokeCap = StrokeCap.Round
            )
            Text(
                "${(state.mergeProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        val isDownloadingPhase = !state.isMerging
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            if (state.info.contains("暂停")) Button(onClick = { viewModel.resumeDownload() }) {
                Text("继续")
            }
            else OutlinedButton(
                onClick = { viewModel.pauseDownload() },
                enabled = isDownloadingPhase
            ) { Text("暂停") }
            TextButton(
                onClick = { viewModel.cancelDownload() },
                enabled = isDownloadingPhase,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("取消") }
        }
    }
}

@Composable
fun HomeSuccessContent(
    state: HomeState.Success,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier.padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎉 ${state.message}", color = MaterialTheme.colorScheme.primary)
        Button(onClick = onReset, modifier = Modifier.padding(top = 24.dp)) { Text("完成") }
    }
}

@Composable
fun HomeErrorContent(
    state: HomeState.Error,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier.padding(top = 100.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("❌ ${state.errorMsg}", color = MaterialTheme.colorScheme.error)
        Button(onClick = onReset, modifier = Modifier.padding(top = 24.dp)) { Text("重试") }
    }
}
