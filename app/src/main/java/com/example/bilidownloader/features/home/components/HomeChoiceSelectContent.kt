package com.example.bilidownloader.features.home.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bilidownloader.features.home.HomeState
import com.example.bilidownloader.features.home.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeChoiceSelectContent(
    state: HomeState.ChoiceSelect,
    viewModel: HomeViewModel,
    onShowSubtitleDialog: () -> Unit,
    formatTime: (Long) -> String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        BiliWebPlayer(
            bvid = state.detail.bvid,
            page = state.selectedPage.page
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(state.detail.title, style = MaterialTheme.typography.titleMedium)
        if (state.detail.pages.size > 1) {
            Spacer(modifier = Modifier.height(8.dp))
            PageSelector(
                pages = state.detail.pages,
                selectedPage = state.selectedPage,
                onPageSelected = { viewModel.updateSelectedPage(it) })
        }
        Spacer(modifier = Modifier.height(16.dp))
        QualitySelector(
            "视频画质",
            state.videoFormats,
            state.selectedVideo
        ) { viewModel.updateSelectedVideo(it) }
        Spacer(modifier = Modifier.height(8.dp))
        QualitySelector(
            "音频音质",
            state.audioFormats,
            state.selectedAudio
        ) { viewModel.updateSelectedAudio(it) }

        val isSourceFlac = state.selectedAudio?.codecs?.contains("flac") == true

        Spacer(modifier = Modifier.height(16.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.ContentCut,
                        null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("自定义裁剪", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = state.isCropEnabled,
                        onCheckedChange = { viewModel.toggleCrop(it) },
                        thumbContent = {
                            if (state.isCropEnabled) Icon(
                                Icons.Default.Check,
                                null,
                                Modifier.size(12.dp)
                            )
                        }
                    )
                }
                if (state.isCropEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    RangeSlider(
                        value = state.cropStart..state.cropEnd,
                        onValueChange = { range ->
                            viewModel.updateCropRange(range.start, range.endInclusive)
                        },
                        valueRange = 0f..state.videoDurationSeconds.toFloat(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            formatTime(state.cropStart.toLong()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "选中时长: ${formatTime((state.cropEnd - state.cropStart).toLong())}",
                            style = MaterialTheme.typography.labelSmall
                        )
                        Text(
                            formatTime(state.cropEnd.toLong()),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Button(
            onClick = { viewModel.startDownload(false) },
            modifier = Modifier.fillMaxWidth()
        ) { Text("下载 MP4 视频") }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { viewModel.startDownload(true) },
                modifier = Modifier.weight(1f)
            ) {
                val ext = if (isSourceFlac) "FLAC" else state.selectedAudioExtension.uppercase()
                Text("仅下载音频 (.$ext)")
            }

            if (!isSourceFlac) {
                SingleChoiceSegmentedButtonRow(modifier = Modifier.width(110.dp)) {
                    SegmentedButton(
                        selected = state.selectedAudioExtension == "m4a",
                        onClick = { viewModel.updateAudioExtension("m4a") },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        icon = {}
                    ) {
                        Text("M4A", style = MaterialTheme.typography.labelSmall)
                    }
                    SegmentedButton(
                        selected = state.selectedAudioExtension == "mp3",
                        onClick = { viewModel.updateAudioExtension("mp3") },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        icon = {}
                    ) {
                        Text("MP3", style = MaterialTheme.typography.labelSmall)
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.MusicNote,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "FLAC",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        FilledTonalButton(
            onClick = onShowSubtitleDialog,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )
        ) {
            Icon(Icons.Default.AutoAwesome, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("AI 智能摘要 / 字幕")
        }
        Spacer(modifier = Modifier.height(48.dp))
    }
}
