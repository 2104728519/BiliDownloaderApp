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
     * 自动处理 Set-Cookie 格式，支持 raw cookie string (key=value; key2=value2)
     * 【优化：支持解析完整的 Cookie 长字符串中的所有键值对】
     */
    fun saveCookies(context: Context, cookieStrings: List<String>) {
        if (cookieStrings.isEmpty()) return

        // 1. 获取现有 Cookie 并解析为 Map
        val currentCookies = getCookieMap(context).toMutableMap()

        // 2. 解析新 Cookie
        for (cookieLine in cookieStrings) {
            // 【核心修复】将整个 cookieLine 按分号分割，获取所有 potential key-value pairs and attributes
            val params = cookieLine.split(";")

            for (param in params) {
                val trimParam = param.trim()
                if (trimParam.isEmpty()) continue

                // 忽略 HttpOnly, Path, Domain 等标准属性（因为我们要存的是键值对）
                if (isStandardAttribute(trimParam)) continue

                // 使用 limit = 2 确保值中包含等号也能正确解析
                val parts = trimParam.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    // 排除无效值
                    if (value.isNotEmpty() && value != "\"\"") {
                        currentCookies[key] = value
                    }
                }
            }
        }

        // 3. 重新拼接并保存
        val newCookieString = currentCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        getPrefs(context).edit().putString(KEY_COOKIE, newCookieString).apply()
    }

    // 辅助方法：判断是否为标准 Cookie 属性（不需要保存到 Map 中的）
    private fun isStandardAttribute(part: String): Boolean {
        val lower = part.lowercase()
        return lower.startsWith("path=") ||
                lower.startsWith("domain=") ||
                lower.startsWith("expires=") ||
                lower.startsWith("max-age=") ||
                lower.equals("httponly") ||
                lower.equals("secure") ||
                lower.startsWith("samesite=")
    }

    // 兼容旧的单条保存方法
    fun saveSessData(context: Context, sessData: String) {
        // 由于 saveCookies 已修复，这里只需保持调用即可
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
     * 获取 Cookie 中指定字段的值
     */
    fun getCookieValue(context: Context, key: String): String? {
        val map = getCookieMap(context)
        return map[key]
    }

    /**
     * 清空所有 Cookie (本地退出)
     */
    fun clearCookies(context: Context) {
        getPrefs(context).edit().remove(KEY_COOKIE).apply()
    }

    // 获取 Map 格式方便解析，用于内部读取
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