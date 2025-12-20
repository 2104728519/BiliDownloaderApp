
package com.example.bilidownloader.data.repository

import com.example.bilidownloader.core.common.Resource
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全局下载会话管理器
 */
object DownloadSession {
    // 【修复】将 replay 改为 0，防止 "状态倒灌" (新页面打开时收到旧的 Loading 状态)
    // extraBufferCapacity = 1 配合 DROP_OLDEST 确保 Service 发射数据时永远不会被挂起
    private val _downloadState = MutableSharedFlow<Resource<String>>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val downloadState = _downloadState.asSharedFlow()

    suspend fun updateState(resource: Resource<String>) {
        _downloadState.emit(resource)
    }
}