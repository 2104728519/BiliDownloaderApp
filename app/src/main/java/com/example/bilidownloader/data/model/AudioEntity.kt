package com.example.bilidownloader.data.model

import android.net.Uri
import com.example.bilidownloader.core.util.DateUtils

/**
 * 领域模型：音频文件信息.
 * 用于 UI 层展示本地扫描到的音频列表.
 */
data class AudioEntity(
    val id: Long,
    val uri: Uri,
    val title: String,
    val duration: Long,
    val size: Long,
    val dateAdded: Long
) {
    // 格式化大小 (如 "3.2 MB")
    val sizeText: String
        get() {
            val kb = size / 1024.0
            val mb = kb / 1024.0
            return when {
                mb >= 1 -> String.format("%.1f MB", mb)
                kb >= 1 -> String.format("%.1f KB", kb)
                else -> "$size B"
            }
        }

    // 格式化时长 (如 "03:45")
    val durationText: String
        get() {
            val seconds = duration / 1000
            val m = seconds / 60
            val s = seconds % 60
            return String.format("%02d:%02d", m, s)
        }

    // 格式化日期 (系统时间戳需转换为毫秒)
    val dateText: String
        get() = DateUtils.format(dateAdded * 1000L)
}