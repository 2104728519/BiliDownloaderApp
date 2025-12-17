package com.example.bilidownloader.data.model

import android.net.Uri
import com.example.bilidownloader.core.util.DateUtils

/**
 * 音频档案卡
 */
data class AudioEntity(
    val id: Long,
    val uri: Uri,
    val title: String,
    val duration: Long,
    val size: Long,
    val dateAdded: Long // 系统给的是“秒”
) {
    // 大小转文字 (3.2 MB)
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

    // 时长转文字 (03:45)
    val durationText: String
        get() {
            val seconds = duration / 1000
            val m = seconds / 60
            val s = seconds % 60
            return String.format("%02d:%02d", m, s)
        }

    // 【新增】日期转文字 (2025-12-07 10:30)
    val dateText: String
        get() {
            // ★关键点：系统给的是秒，Java的日期工具要毫秒，必须 * 1000
            return DateUtils.format(dateAdded * 1000L)
        }
}