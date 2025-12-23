package com.example.bilidownloader.domain.usecase

import com.example.bilidownloader.data.model.VideoDetail
import com.example.bilidownloader.ui.state.FormatOption

/**
 * 领域层模型：视频解析结果.
 *
 * 将 API 返回的复杂嵌套数据 (VideoDetail, DashInfo) 聚合为 UI 层易用的结构。
 * @property videoFormats 清洗、排序后的可用视频流选项.
 * @property audioFormats 清洗、排序后的可用音频流选项.
 */
data class VideoAnalysisResult(
    val detail: VideoDetail,
    val videoFormats: List<FormatOption>,
    val audioFormats: List<FormatOption>
)