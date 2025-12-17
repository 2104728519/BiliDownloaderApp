package com.example.bilidownloader.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日期时间工具类
 * 负责时间戳与可读字符串之间的转换
 */
object DateUtils {

    private const val PATTERN_DEFAULT = "yyyy-MM-dd HH:mm"
    private const val PATTERN_FULL = "yyyy-MM-dd HH:mm:ss"

    /**
     * 将毫秒级时间戳格式化为 "yyyy-MM-dd HH:mm"
     * @param timestamp 毫秒级时间戳
     * @return 格式化后的字符串
     */
    fun format(timestamp: Long): String {
        return SimpleDateFormat(PATTERN_DEFAULT, Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * 将毫秒级时间戳格式化为完整格式 "yyyy-MM-dd HH:mm:ss"
     */
    fun formatFull(timestamp: Long): String {
        return SimpleDateFormat(PATTERN_FULL, Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * 将秒级时长转换为 "MM:ss" 或 "HH:mm:ss" 格式
     * @param durationSeconds 视频时长（秒）
     */
    fun formatDuration(durationSeconds: Long): String {
        val hours = durationSeconds / 3600
        val minutes = (durationSeconds % 3600) / 60
        val seconds = durationSeconds % 60

        return if (hours > 0) {
            String.format(Locale.US, "%02d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format(Locale.US, "%02d:%02d", minutes, seconds)
        }
    }
}