package com.example.bilidownloader.data.model

import com.google.gson.annotations.SerializedName

// region AI Conclusion (AI 摘要与字幕)

/**
 * AI 摘要接口响应根对象.
 * 对应 API: /x/web-interface/view/conclusion/get
 */
data class ConclusionResponse(
    val code: Int,
    val message: String?,
    val data: ConclusionData?
)

data class ConclusionData(
    @SerializedName("model_result")
    val modelResult: ModelResult?,

    // 摘要唯一 ID (stid)，用于点赞/反馈机制 (预留)
    val stid: String?
)

data class ModelResult(
    val summary: String?,            // AI 生成的视频一句话总结
    val outline: List<OutlineItem>?, // 视频章节大纲 (带时间戳)
    val subtitle: List<SubtitleContainer>? // AI 识别的字幕容器
)

data class OutlineItem(
    val title: String,
    val timestamp: Long,
    @SerializedName("part_outline")
    val partOutline: List<PartOutlineItem>?
)

data class PartOutlineItem(
    val timestamp: Long,
    val content: String
)

data class SubtitleContainer(
    @SerializedName("part_subtitle")
    val partSubtitle: List<PartSubtitleItem>?,
    val lan: String? = null // 语言标识 (如 "zh-CN")
)

data class PartSubtitleItem(
    @SerializedName("start_timestamp")
    val startTimestamp: Long,
    @SerializedName("end_timestamp")
    val endTimestamp: Long,
    val content: String
)

// endregion