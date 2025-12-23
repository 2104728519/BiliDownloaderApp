package com.example.bilidownloader.core.util

import java.util.regex.Pattern

/**
 * 链接与 ID 提取工具.
 * 使用正则表达式从混合文本中识别并提取 B 站视频 ID (BV号).
 */
object LinkUtils {

    /**
     * BV 号正则模式.
     * 格式：BV + 10位字母数字组合.
     */
    private const val BV_PATTERN = "BV[a-zA-Z0-9]{10}"

    /**
     * 尝试从文本中提取第一个匹配的 BV 号.
     *
     * @param text 可能包含 URL 或其他文字的输入字符串.
     * @return 提取到的 BV 号 (如 "BV1mdUbB7Etn")，若无匹配则返回 null.
     */
    fun extractBvid(text: String): String? {
        if (text.isBlank()) return null

        val pattern = Pattern.compile(BV_PATTERN)
        val matcher = pattern.matcher(text)

        if (matcher.find()) {
            return matcher.group()
        }

        return null
    }
}