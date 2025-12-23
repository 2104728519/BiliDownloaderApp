package com.example.bilidownloader.core.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 日期时间格式化工具类.
 * 负责时间戳到可读字符串的转换，以及时长的格式化.
 */
object DateUtils {

    private const val PATTERN_DEFAULT = "yyyy-MM-dd HH:mm"
    private const val PATTERN_FULL = "yyyy-MM-dd HH:mm:ss"

    /**
     * 格式化: "yyyy-MM-dd HH:mm"
     */
    fun format(timestamp: Long): String {
        return SimpleDateFormat(PATTERN_DEFAULT, Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * 格式化: "yyyy-MM-dd HH:mm:ss"
     */
    fun formatFull(timestamp: Long): String {
        return SimpleDateFormat(PATTERN_FULL, Locale.getDefault()).format(Date(timestamp))
    }

    /**
     * 将秒数格式化为时分秒格式 (例如 "01:30:05" 或 "04:20").
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