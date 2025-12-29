// 文件: core/database/FfmpegPresetEntity.kt
package com.example.bilidownloader.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ffmpeg_presets")
data class FfmpegPresetEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,           // 预设名称 (如: "提取音频MP3")
    val commandArgs: String,    // 参数部分 (如: "-vn -c:a libmp3lame")
    val outputExtension: String,// 后缀 (如: ".mp3")
    val timestamp: Long = System.currentTimeMillis() // 用于排序
)