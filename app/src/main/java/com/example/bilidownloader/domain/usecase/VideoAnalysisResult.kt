package com.example.bilidownloader.domain.usecase

import com.example.bilidownloader.data.model.VideoDetail
import com.example.bilidownloader.ui.state.FormatOption

/**
 * 视频解析结果的领域模型
 * 将 API 返回的零散数据整合成 UI 易于使用的格式
 */
data class VideoAnalysisResult(
    val detail: VideoDetail,
    val videoFormats: List<FormatOption>,
    val audioFormats: List<FormatOption>
)