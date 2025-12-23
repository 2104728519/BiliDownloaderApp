package com.example.bilidownloader.data.model

import com.google.gson.annotations.SerializedName

/**
 * 播放器配置接口 V2 响应.
 * 包含 CC 字幕列表 (Plan B 兜底方案).
 */
data class PlayerV2Response(
    val code: Int,
    val data: PlayerV2Data?
)

data class PlayerV2Data(
    val subtitle: PlayerSubtitleInfo?
)

data class PlayerSubtitleInfo(
    val subtitles: List<SubtitleItem>?
)

data class SubtitleItem(
    val id: Long,
    val lan: String,
    @SerializedName("lan_doc") val lanDoc: String, // 字幕语言描述 (如 "中文")
    @SerializedName("subtitle_url") val subtitleUrl: String // JSON 文件 URL
)

/**
 * 原始字幕 JSON 文件结构.
 */
data class RawSubtitleJson(
    val body: List<RawSubtitleItem>?
)

data class RawSubtitleItem(
    val from: Double, // 开始时间 (秒)
    val to: Double,   // 结束时间 (秒)
    val content: String
)