package com.example.bilidownloader.utils

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DateUtils {
    // 格式化模版：年-月-日 时:分
    private val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun format(timestamp: Long): String {
        return formatter.format(Date(timestamp))
    }
}