// 文件: features/ffmpeg/FfmpegState.kt
package com.example.bilidownloader.features.ffmpeg

/**
 * FFmpeg 任务执行的实时状态.
 */
sealed class FfmpegTaskState {
    object Idle : FfmpegTaskState()

    data class Running(
        val progress: Float,      // 0.0 ~ 1.0
        val logs: List<String>,   // 累计日志
        val duration: Long = 0L,  // 视频总时长(ms)，用于计算进度
        val currentCmd: String    // 当前执行的完整命令
    ) : FfmpegTaskState()

    data class Success(
        val outputUri: String,    // 保存到相册后的 URI
        val logs: List<String>,
        val costTime: Long        // 耗时(ms)
    ) : FfmpegTaskState()

    data class Error(
        val message: String,
        val logs: List<String>
    ) : FfmpegTaskState()
}