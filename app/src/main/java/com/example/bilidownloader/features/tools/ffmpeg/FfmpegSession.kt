// 文件: features/ffmpeg/FfmpegSession.kt
package com.example.bilidownloader.features.ffmpeg

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * FFmpeg 全局会话管理器.
 *
 * 职责：
 * 1. 维护 FFmpeg 任务的全局状态。
 * 2. 作为 Service (后台) 和 ViewModel (UI) 之间的通信桥梁。
 * 3. 保证即使页面销毁，任务状态依然保留。
 */
object FfmpegSession {
    // 默认状态为 Idle
    private val _taskState = MutableStateFlow<FfmpegTaskState>(FfmpegTaskState.Idle)
    val taskState = _taskState.asStateFlow()

    /**
     * 更新任务状态
     */
    fun updateState(state: FfmpegTaskState) {
        _taskState.value = state
    }

    /**
     * 重置状态 (通常在任务结束或出错且用户确认后调用)
     */
    fun reset() {
        _taskState.value = FfmpegTaskState.Idle
    }
}