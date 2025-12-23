package com.example.bilidownloader.data.repository

import com.example.bilidownloader.core.common.Resource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全局下载会话管理器.
 *
 * 充当 Service (后台服务) 和 ViewModel (UI层) 之间的消息总线。
 * 使用 SharedFlow 广播下载状态，支持多个观察者（如通知栏和进度条）。
 * 配置了 DROP_OLDEST 策略，确保 UI 滞后时不会阻塞下载线程。
 */
object DownloadSession {
    private val _downloadState = MutableSharedFlow<Resource<String>>(
        replay = 0, // 不重播旧状态，防止页面重建时显示过期的 Loading
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val downloadState = _downloadState.asSharedFlow()

    suspend fun updateState(resource: Resource<String>) {
        _downloadState.emit(resource)
    }
}