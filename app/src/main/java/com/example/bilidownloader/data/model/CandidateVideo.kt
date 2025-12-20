package com.example.bilidownloader.data.model

/**
 * 候选视频模型
 * 包含推荐流里的原始信息 + 预先获取的字幕数据
 */
data class CandidateVideo(
    val info: RecommendItem,          // 视频基础信息 (标题、封面、BVID)
    val subtitleData: ConclusionData, // 已获取的字幕数据 (这是关键，避免二次请求)
    val cid: Long                     // 对应的 CID (分P ID)
)