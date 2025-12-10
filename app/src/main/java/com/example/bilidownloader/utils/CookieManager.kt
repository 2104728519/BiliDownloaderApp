package com.example.bilidownloader.utils

import android.content.Context
import android.content.SharedPreferences

object CookieManager {

    private const val PREFS_NAME = "BiliDownloaderPrefs"
    private const val KEY_SESS_DATA = "SESSDATA"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存 SESSDATA 值
     * @param context Application 上下文
     * @param sessData 用户输入的 SESSDATA 字符串
     */
    fun saveSessData(context: Context, sessData: String) {
        val editor = getPrefs(context).edit()
        // 注意：只保存 SESSDATA=... 等号后面的值，并去除可能的前后空格
        val pureSessData = sessData.substringAfter("=").trim()
        editor.putString(KEY_SESS_DATA, pureSessData)
        editor.apply()
    }

    /**
     * 获取完整的 Cookie 字符串 (例如："SESSDATA=xxxxxx")
     * @param context Application 上下文
     * @return 如果存在，返回完整的 Cookie 字符串；否则返回 null
     */
    fun getCookie(context: Context): String? {
        val sessData = getPrefs(context).getString(KEY_SESS_DATA, null)
        return if (!sessData.isNullOrBlank()) {
            "SESSDATA=$sessData"
        } else {
            null
        }
    }

    /**
     * 获取 SESSDATA 的值部分，用于在输入框中显示
     * @param context Application 上下文
     * @return 返回存储的 SESSDATA 值，可能为空
     */
    fun getSessDataValue(context: Context): String {
        return getPrefs(context).getString(KEY_SESS_DATA, "") ?: ""
    }
}