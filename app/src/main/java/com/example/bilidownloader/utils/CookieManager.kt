package com.example.bilidownloader.utils

import android.content.Context
import android.content.SharedPreferences

object CookieManager {

    private const val PREFS_NAME = "BiliDownloaderPrefs"
    private const val KEY_COOKIE = "FULL_COOKIE"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 保存/合并 Cookie
     * 自动处理 Set-Cookie 格式，例如 "buvid3=xxx; Path=/; Domain=..."
     */
    fun saveCookies(context: Context, cookieStrings: List<String>) {
        if (cookieStrings.isEmpty()) return

        // 1. 获取现有 Cookie 并解析为 Map
        val currentCookies = getCookieMap(context).toMutableMap()

        // 2. 解析新 Cookie 并覆盖旧值
        for (cookieLine in cookieStrings) {
            // 解析 "key=value; other..." 中的 key=value
            val pair = cookieLine.split(";").firstOrNull() ?: continue
            val parts = pair.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                // 排除无效值
                if (value.isNotEmpty() && value != "\"\"") {
                    currentCookies[key] = value
                }
            }
        }

        // 3. 重新拼接并保存
        val newCookieString = currentCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        getPrefs(context).edit().putString(KEY_COOKIE, newCookieString).apply()
    }

    // 兼容旧的单条保存方法
    fun saveSessData(context: Context, sessData: String) {
        saveCookies(context, listOf(sessData))
    }

    /**
     * 获取完整的 Cookie 字符串用于请求头
     */
    fun getCookie(context: Context): String? {
        val cookie = getPrefs(context).getString(KEY_COOKIE, "")
        return if (cookie.isNullOrBlank()) null else cookie
    }

    /**
     * 【新增】获取 Cookie 中指定字段的值
     * 用于获取 bili_jct (即 CSRF Token)
     */
    fun getCookieValue(context: Context, key: String): String? {
        val map = getCookieMap(context)
        return map[key]
    }

    /**
     * 【新增】清空所有 Cookie (本地退出)
     */
    fun clearCookies(context: Context) {
        getPrefs(context).edit().remove(KEY_COOKIE).apply()
    }

    // 获取 Map 格式方便解析
    private fun getCookieMap(context: Context): Map<String, String> {
        val cookieString = getPrefs(context).getString(KEY_COOKIE, "") ?: return emptyMap()
        val map = mutableMapOf<String, String>()
        cookieString.split(";").forEach {
            val parts = it.split("=", limit = 2)
            if (parts.size == 2) {
                map[parts[0].trim()] = parts[1].trim()
            }
        }
        return map
    }

    fun getSessDataValue(context: Context): String {
        return getCookieMap(context)["SESSDATA"] ?: ""
    }
}