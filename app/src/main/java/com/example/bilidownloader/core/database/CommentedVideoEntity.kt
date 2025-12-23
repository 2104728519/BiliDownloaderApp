package com.example.bilidownloader.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 已自动化评论过的视频实体.
 * 充当本地布隆过滤器的角色，防止重复请求.
 */
@Entity(tableName = "commented_videos")
data class CommentedVideoEntity(
    @PrimaryKey
    val bvid: String,
    val oid: Long,
    val title: String,
    val timestamp: Long = System.currentTimeMillis()
)