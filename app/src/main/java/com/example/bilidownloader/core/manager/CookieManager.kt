package com.example.bilidownloader.core.manager

import android.content.Context
import android.content.SharedPreferences
import com.example.bilidownloader.core.common.Constants

/**
 * Cookie 管理器
 * 负责 Cookie 的持久化存储、解析与提取
 */
object CookieManager {

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 【新增】从原始 Cookie 字符串中解析出键值对
     * 无论用户粘贴的是 "key=value" 还是 "key1=val1; key2=val2" 都能搞定
     * 该方法不依赖 Context，可用于纯字符串处理
     */
    fun parseCookieStringToMap(cookieString: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        if (cookieString.isBlank()) return map

        val items = cookieString.split(";")
        for (item in items) {
            val trimItem = item.trim()
            if (trimItem.isEmpty()) continue

            val parts = trimItem.split("=", limit = 2)
            if (parts.size == 2) {
                val key = parts[0].trim()
                val value = parts[1].trim()
                if (key.isNotEmpty()) {
                    map[key] = value
                }
            }
        }
        return map
    }

    /**
     * 保存并合并 Cookie 列表
     * 会自动解析 Set-Cookie 字符串，提取键值对并与现有 Cookie 合并
     */
    fun saveCookies(context: Context, cookieStrings: List<String>) {
        if (cookieStrings.isEmpty()) return

        // 1. 获取现有 Cookie 并解析为 Map
        val currentCookies = getCookieMap(context).toMutableMap()

        // 2. 解析新 Cookie
        for (cookieLine in cookieStrings) {
            val params = cookieLine.split(";")
            for (param in params) {
                val trimParam = param.trim()
                if (trimParam.isEmpty()) continue

                // 忽略 HttpOnly, Path, Domain 等标准属性
                if (isStandardAttribute(trimParam)) continue

                // 解析键值对
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
        getPrefs(context).edit().putString(Constants.KEY_BILI_FULL_COOKIE, newCookieString).apply()
    }

    /**
     * 兼容旧方法：保存单个 SessData 字符串
     */
    fun saveSessData(context: Context, sessData: String) {
        saveCookies(context, listOf(sessData))
    }

    /**
     * 获取用于 HTTP 请求头的完整 Cookie 字符串
     */
    fun getCookie(context: Context): String? {
        val cookie = getPrefs(context).getString(Constants.KEY_BILI_FULL_COOKIE, "")
        return if (cookie.isNullOrBlank()) null else cookie
    }

    /**
     * 获取 Cookie 中指定字段的值
     */
    fun getCookieValue(context: Context, key: String): String? {
        return getCookieMap(context)[key]
    }

    /**
     * 获取 SESSDATA 值
     */
    fun getSessDataValue(context: Context): String {
        return getCookieValue(context, "SESSDATA") ?: ""
    }

    /**
     * 清空 B 站相关 Cookie
     */
    fun clearCookies(context: Context) {
        getPrefs(context).edit().remove(Constants.KEY_BILI_FULL_COOKIE).apply()
    }

    // ========================================================================
    // 阿里云控制台凭证管理
    // ========================================================================

    fun saveAliyunConsoleCredentials(context: Context, cookie: String, secToken: String) {
        getPrefs(context).edit()
            .putString(Constants.KEY_ALIYUN_COOKIE, cookie)
            .putString(Constants.KEY_ALIYUN_TOKEN, secToken)
            .apply()
    }

    fun getAliyunConsoleCookie(context: Context): String? {
        return getPrefs(context).getString(Constants.KEY_ALIYUN_COOKIE, null)
    }

    fun getAliyunConsoleSecToken(context: Context): String? {
        return getPrefs(context).getString(Constants.KEY_ALIYUN_TOKEN, null)
    }

    // ========================================================================
    // 内部辅助方法
    // ========================================================================

    private fun getCookieMap(context: Context): Map<String, String> {
        val cookieString = getPrefs(context).getString(Constants.KEY_BILI_FULL_COOKIE, "") ?: ""
        // 直接复用新添加的解析方法
        return parseCookieStringToMap(cookieString)
    }

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
}