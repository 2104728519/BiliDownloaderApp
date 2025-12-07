package com.example.bilidownloader.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 这就是我们的“记账本”的一页纸
 * @Entity 表示这是一张数据库表
 * tableName = "history" 给表起个名字叫 history
 */
@Entity(tableName = "history")
data class HistoryEntity(
    // @PrimaryKey: 主键，也就是唯一的身份证号
    // 我们直接用 BV 号做主键，这样如果同一个视频解析两次，新的会覆盖旧的，不会重复
    @PrimaryKey
    val bvid: String,

    val title: String,      // 标题
    val coverUrl: String,   // 封面图链接
    val uploader: String,   // UP主名字
    val timestamp: Long     // 记录时间 (用来排序，最新的排前面)
)