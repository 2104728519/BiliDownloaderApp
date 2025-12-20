package com.example.bilidownloader.data.model

import com.google.gson.annotations.SerializedName

// 1. 播放器配置接口响应
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
    @SerializedName("lan_doc") val lanDoc: String, // 例如 "中文（自动生成）"
    @SerializedName("subtitle_url") val subtitleUrl: String // 字幕 JSON 文件地址
)

// 2. 字幕 JSON 文件内容结构 (从 subtitle_url 下载下来的内容)
data class RawSubtitleJson(
    val body: List<RawSubtitleItem>?
)

data class RawSubtitleItem(
    val from: Double, // 开始时间 (秒)
    val to: Double,   // 结束时间 (秒)
    val content: String // 字幕文本
)