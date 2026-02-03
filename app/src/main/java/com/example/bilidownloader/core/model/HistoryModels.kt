package com.example.bilidownloader.core.model

import com.example.bilidownloader.core.util.DateUtils

/**
 * B 站云端历史记录响应包装类.
 */
data class BiliHistoryResponse(
    val code: Int,
    val message: String?,
    val data: HistoryData?
)

data class HistoryData(
    val cursor: HistoryCursor?,
    val list: List<CloudHistoryItem>?
)

/**
 * 分页游标.
 * 下一次请求时需要将这两个值传回去.
 */
data class HistoryCursor(
    val max: Long,    // 游标 ID
    val view_at: Long // 游标时间戳
)

/**
 * 单条云端历史记录.
 */
data class CloudHistoryItem(
    val title: String,
    val cover: String,        // 封面图 URL
    val author_name: String,  // UP主名称
    val view_at: Long,        // 观看时间 (秒级时间戳)
    val kid: Long,            // 记录唯一 ID (用于 LazyColumn 的 key)
    val history: HistoryMeta? // 嵌套的元数据 (包含 bvid)
) {
    // 辅助属性：安全获取 BVID
    val bvid: String
        get() = history?.bvid ?: ""

    // 辅助属性：格式化观看时间 (将秒转为毫秒后格式化)
    val viewDateText: String
        get() = DateUtils.format(view_at * 1000L)
}

/**
 * 历史记录核心元数据
 */
data class HistoryMeta(
    val oid: Long,
    val bvid: String,
    val business: String // 通常为 "archive"
)