package com.example.bilidownloader.core.model

/**
 * 首页推荐流响应结构.
 */
data class RecommendResponse(
    val code: Int,
    val message: String?,
    val data: RecommendData?
)

data class RecommendData(
    val item: List<RecommendItem>?
)

data class RecommendItem(
    val id: Long,        // 视频 oid
    val bvid: String,
    val title: String,
    val pic: String,     // 封面图
    val uri: String,     // 唤起 App 的 URI
    val owner: RecommendOwner?,
    val stat: RecommendStat?
)

data class RecommendOwner(
    val mid: Long,
    val name: String,
    val face: String
)

data class RecommendStat(
    val view: Long,
    val like: Long,
    val danmaku: Long
)