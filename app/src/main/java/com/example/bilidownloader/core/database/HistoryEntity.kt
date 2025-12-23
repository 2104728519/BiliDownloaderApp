package com.example.bilidownloader.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 历史记录实体.
 * 以 bvid 作为主键，确保同一个视频只保留一条最新记录.
 */
@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey
    val bvid: String,
    val title: String,
    val coverUrl: String,
    val uploader: String,
    val timestamp: Long
)