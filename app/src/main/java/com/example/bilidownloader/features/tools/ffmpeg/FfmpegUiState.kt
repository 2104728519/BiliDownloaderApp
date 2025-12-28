// 文件: features/ffmpeg/FfmpegUiState.kt
package com.example.bilidownloader.features.ffmpeg

import android.net.Uri

/**
 * FFmpeg 终端页面的 UI 状态
 */
data class FfmpegUiState(
    // --- 输入槽位 (Slot 1: Input) ---
    val inputFileUri: Uri? = null,
    val inputFileName: String = "",
    val inputFileSize: String = "",

    // --- 参数槽位 (Slot 2-4: Args) ---
    // 这是用户可编辑的区域，未来也可由预设自动填充
    val arguments: String = "-c:v libx264 -crf 23 -preset ultrafast",

    // --- 输出槽位 (Slot 5: Output) ---
    // 默认为 mp4，用户可以通过修改参数触发自动变更(需要额外逻辑，暂定手动选择或默认)
    val outputExtension: String = ".mp4",

    // --- 控制台状态 ---
    val taskState: FfmpegTaskState = FfmpegTaskState.Idle
)