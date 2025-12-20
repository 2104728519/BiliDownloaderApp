package com.example.bilidownloader.data.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已处理/已评论视频记录表
 * 用于本地过滤，防止重复处理同一个视频
 */
@Entity(tableName = "commented_videos")
data class CommentedVideoEntity(
    @PrimaryKey
    val bvid: String,
    val oid: Long,
    val title: String,
    val timestamp: Long = System.currentTimeMillis() // 处理时间
)