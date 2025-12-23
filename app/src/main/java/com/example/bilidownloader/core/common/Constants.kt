package com.example.bilidownloader.core.common

/**
 * 全局常量配置表.
 * 集中管理 API 端点、HTTP 请求头参数、本地存储键名及业务默认配置.
 */
object Constants {

    // region Network Configuration (网络配置)

    const val BILI_BASE_URL = "https://api.bilibili.com/"
    const val PASSPORT_BASE_URL = "https://passport.bilibili.com/"
    const val ALIYUN_BASE_URL = "https://dashscope.aliyuncs.com/"
    const val ALIYUN_CONSOLE_BASE_URL = "https://bailian-cs.console.aliyun.com/"

    /**
     * 模拟 Windows PC 端 Chrome 浏览器的 User-Agent.
     * 用于通过 B 站的基础风控检查，防止请求被识别为移动端爬虫.
     */
    const val COMMON_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    const val HEADER_REFERER = "Referer"
    const val HEADER_USER_AGENT = "User-Agent"
    const val HEADER_COOKIE = "Cookie"
    const val BILI_REFERER = "https://www.bilibili.com"

    // endregion

    // region Local Storage (本地存储)

    const val PREFS_NAME = "BiliDownloaderPrefs"

    /** SharedPreferences Key: 存储拼接好的完整 B 站 Cookie 字符串 */
    const val KEY_BILI_FULL_COOKIE = "FULL_COOKIE"

    /** SharedPreferences Key: 阿里云控制台 Cookie (用于爬取用量) */
    const val KEY_ALIYUN_COOKIE = "ALIYUN_CONSOLE_COOKIE"

    /** SharedPreferences Key: 阿里云控制台 Security Token */
    const val KEY_ALIYUN_TOKEN = "ALIYUN_CONSOLE_SEC_TOKEN"

    // endregion

    // region Business Logic (业务逻辑)

    /** 阿里云百炼/听悟 API 默认使用的语音识别模型版本 */
    const val ALIYUN_ASR_MODEL = "paraformer-v2"

    /** App 在外部存储中创建的默认文件夹名称 */
    const val DIR_NAME = "BiliDownloader"

    // endregion
}