package com.example.bilidownloader.data.repository

import com.example.bilidownloader.core.common.Resource
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 全局下载会话管理器
 * Service 负责生产数据，ViewModel 负责消费数据
 */
object DownloadSession {
    // SharedFlow 支持多个观察者（比如首页和详情页同时看进度）
    // replay = 1 确保 UI 重建（比如旋转屏幕）后能立即获得最新进度
    private val _downloadState = MutableSharedFlow<Resource<String>>(replay = 1)
    val downloadState = _downloadState.asSharedFlow()

    suspend fun updateState(resource: Resource<String>) {
        _downloadState.emit(resource)
    }

    // 如果以后要做多任务，可以在这里加一个 Map<Bvid, Flow>
}