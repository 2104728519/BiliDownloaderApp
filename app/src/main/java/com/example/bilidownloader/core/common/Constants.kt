package com.example.bilidownloader.core.common

/**
 * 全局常量定义
 * 集中管理 API 地址、请求头参数、存储键名等
 */
object Constants {

    // ========================================================================
    // 网络配置 (Network Configuration)
    // ========================================================================

    const val BILI_BASE_URL = "https://api.bilibili.com/"
    const val PASSPORT_BASE_URL = "https://passport.bilibili.com/"
    const val ALIYUN_BASE_URL = "https://dashscope.aliyuncs.com/"
    const val ALIYUN_CONSOLE_BASE_URL = "https://bailian-cs.console.aliyun.com/"

    /**
     * 通用 User-Agent，伪装成 Windows PC 端的 Chrome 浏览器
     * 用于通过 B 站的风控检查
     */
    const val COMMON_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    const val HEADER_REFERER = "Referer"
    const val HEADER_USER_AGENT = "User-Agent"
    const val HEADER_COOKIE = "Cookie"
    const val BILI_REFERER = "https://www.bilibili.com"

    // ========================================================================
    // 本地存储 (SharedPreferences)
    // ========================================================================

    const val PREFS_NAME = "BiliDownloaderPrefs"

    /** 存储完整 B 站 Cookie 串的 Key */
    const val KEY_BILI_FULL_COOKIE = "FULL_COOKIE"

    /** 阿里云控制台 Cookie */
    const val KEY_ALIYUN_COOKIE = "ALIYUN_CONSOLE_COOKIE"

    /** 阿里云控制台 SecToken */
    const val KEY_ALIYUN_TOKEN = "ALIYUN_CONSOLE_SEC_TOKEN"

    // ========================================================================
    // 业务逻辑常量
    // ========================================================================

    /** 阿里云百炼/听悟 API 的默认模型 */
    const val ALIYUN_ASR_MODEL = "paraformer-v2"

    /** 默认下载目录名称 */
    const val DIR_NAME = "BiliDownloader"
}