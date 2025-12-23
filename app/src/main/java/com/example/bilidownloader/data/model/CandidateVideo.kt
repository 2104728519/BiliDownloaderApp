package com.example.bilidownloader.data.model

/**
 * 业务模型：自动化评论的候选视频.
 * 组合了推荐流的基础信息、字幕数据和关键的 CID.
 */
data class CandidateVideo(
    val info: RecommendItem,
    val subtitleData: ConclusionData?, // 初始可能为 null，需二次获取
    val cid: Long
)