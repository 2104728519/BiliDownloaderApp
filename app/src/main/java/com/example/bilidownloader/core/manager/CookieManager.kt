package com.example.bilidownloader.core.manager

import android.content.Context
import android.content.SharedPreferences
import com.example.bilidownloader.core.common.Constants

/**
 * Cookie 生命周期管理器.
 * 负责 Cookie 的解析、持久化存储、提取以及向网络请求中注入.
 * 核心逻辑包括处理 B 站复杂的 Cookie 字段 (如 SESSDATA, bili_jct) 和阿里云凭证.
 */
object CookieManager {

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * 解析 Cookie 字符串为键值对 Map.
     * 兼容 "key=value" 单项或 "key1=val1; key2=val2" 复合字符串.
     * 用于处理用户手动粘贴的 Cookie 或 WebView 拦截到的原始数据.
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
     * 保存并合并 Cookie 列表.
     * 将新获取的 Cookie 列表 (如 Response Header 中的 Set-Cookie) 解析并合并到本地存储中.
     * 会自动过滤标准 HTTP 属性 (如 Path, HttpOnly)，只保留业务键值对.
     */
    fun saveCookies(context: Context, cookieStrings: List<String>) {
        if (cookieStrings.isEmpty()) return

        // 1. 加载现有 Cookie
        val currentCookies = getCookieMap(context).toMutableMap()

        // 2. 解析并合并新 Cookie
        for (cookieLine in cookieStrings) {
            val params = cookieLine.split(";")
            for (param in params) {
                val trimParam = param.trim()
                if (trimParam.isEmpty()) continue

                // 过滤非业务属性
                if (isStandardAttribute(trimParam)) continue

                val parts = trimParam.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = parts[0].trim()
                    val value = parts[1].trim()
                    // 仅保存有效值，排除空字符串或占位符
                    if (value.isNotEmpty() && value != "\"\"") {
                        currentCookies[key] = value
                    }
                }
            }
        }

        // 3. 序列化并持久化
        val newCookieString = currentCookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        getPrefs(context).edit().putString(Constants.KEY_BILI_FULL_COOKIE, newCookieString).apply()
    }

    /**
     * 快捷保存单个 SESSDATA (兼容旧代码逻辑).
     */
    fun saveSessData(context: Context, sessData: String) {
        saveCookies(context, listOf(sessData))
    }

    /**
     * 获取完整的 Cookie 字符串，用于 HTTP 请求头.
     * @return 格式为 "key1=val1; key2=val2" 的字符串，若无则返回 null.
     */
    fun getCookie(context: Context): String? {
        val cookie = getPrefs(context).getString(Constants.KEY_BILI_FULL_COOKIE, "")
        return if (cookie.isNullOrBlank()) null else cookie
    }

    /**
     * 获取特定 Cookie 键的值 (如 "bili_jct" 用于 CSRF Token).
     */
    fun getCookieValue(context: Context, key: String): String? {
        return getCookieMap(context)[key]
    }

    /**
     * 获取 SESSDATA 值 (用户身份凭证).
     */
    fun getSessDataValue(context: Context): String {
        return getCookieValue(context, "SESSDATA") ?: ""
    }

    /**
     * 清除 B 站相关的所有 Cookie 数据.
     */
    fun clearCookies(context: Context) {
        getPrefs(context).edit().remove(Constants.KEY_BILI_FULL_COOKIE).apply()
    }

    // region Aliyun Console Credentials (阿里云控制台凭证)

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

    // endregion

    // region Internal Helpers

    private fun getCookieMap(context: Context): Map<String, String> {
        val cookieString = getPrefs(context).getString(Constants.KEY_BILI_FULL_COOKIE, "") ?: ""
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

    // endregion
}