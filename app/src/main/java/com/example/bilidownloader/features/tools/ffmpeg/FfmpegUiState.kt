// 文件位置: features/ffmpeg/FfmpegUiState.kt
package com.example.bilidownloader.features.ffmpeg

import android.net.Uri

/**
 * FFmpeg 终端页面的 UI 状态
 * * 采用槽位系统设计，将复杂命令拆解为可感知的数据字段。
 */
data class FfmpegUiState(
    // --- 输入槽位 (Slot 1: Input) ---
    val inputFileUri: Uri? = null,
    val inputFileName: String = "",
    val inputFileSize: String = "",

    // [新增] 存储详细的媒体信息 (通常为 FFprobe 返回的 JSON 字符串)
    // 用于给 AI 分析或在详情面板展示编码信息
    val mediaInfo: String = "",

    // --- 参数槽位 (Slot 2-4: Args) ---
    // 用户可编辑的 FFmpeg 核心参数区域
    val arguments: String = "",

    // --- 输出槽位 (Slot 5: Output) ---
    // 决定输出文件的封装格式
    val outputExtension: String = ".mp4",

    // --- 执行与控制台状态 ---
    val taskState: FfmpegTaskState = FfmpegTaskState.Idle
)