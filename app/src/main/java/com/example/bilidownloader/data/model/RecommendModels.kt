package com.example.bilidownloader.data.model

import com.google.gson.annotations.SerializedName

/**
 * 首页推荐流响应
 */
data class RecommendResponse(
    val code: Int,
    val message: String?,
    val data: RecommendData?
)

data class RecommendData(
    val item: List<RecommendItem>?
)

/**
 * 单个推荐视频项
 */
data class RecommendItem(
    val id: Long,        // 对应 oid/aid
    val bvid: String,
    val title: String,
    val pic: String,     // 封面图
    val uri: String,     // 跳转链接 (bilibili://video/...)
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