package com.example.bilidownloader.data.model

import com.google.gson.annotations.SerializedName

/**
 * AI 摘要/字幕 接口响应根对象
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

    // 摘要唯一的 ID (stid)，用于点赞/点踩，虽然暂时不用，但建议保留
    val stid: String?
)

data class ModelResult(
    val summary: String?, // AI 生成的一句话总结
    val outline: List<OutlineItem>?, // 视频大纲（带时间戳的章节）
    val subtitle: List<SubtitleContainer>? // 重点：AI 字幕容器，可能有多种语言或版本
)

data class OutlineItem(
    val title: String,
    val timestamp: Long, // 秒级时间戳
    @SerializedName("part_outline")
    val partOutline: List<PartOutlineItem>?
)

data class PartOutlineItem(
    val timestamp: Long,
    val content: String
)

data class SubtitleContainer(
    // 这里的 part_subtitle 才是真正的字幕列表
    @SerializedName("part_subtitle")
    val partSubtitle: List<PartSubtitleItem>?,

    // 有时候 B 站会返回语言标识，比如 "zh-CN"，预留字段
    val lan: String? = null
)

data class PartSubtitleItem(
    @SerializedName("start_timestamp")
    val startTimestamp: Long, // 开始时间 (秒)
    @SerializedName("end_timestamp")
    val endTimestamp: Long,   // 结束时间 (秒)
    val content: String       // 字幕文本
)