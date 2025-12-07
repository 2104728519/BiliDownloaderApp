package com.example.bilidownloader.utils

import java.util.regex.Pattern

/**
 * 链接提取工具
 * 专门负责从一堆乱七八糟的文字里，精准抓住 BV 号
 */
object LinkUtils {

    // 正则表达式：这是给机器看的“通缉令”
    // BV: 必须以 BV 开头
    // [a-zA-Z0-9]: 后面跟着字母或数字
    // {10}: 后面正好有 10 位 (BV号标准格式通常是 BV + 10位字符)
    private const val BV_PATTERN = "BV[a-zA-Z0-9]{10}"

    /**
     * 从文本中提取 BV 号
     * 输入: "快看这个视频！https://bilibili.com/video/BV1mdUbB7Etn?spm=..."
     * 输出: "BV1mdUbB7Etn"
     */
    fun extractBvid(text: String): String? {
        if (text.isBlank()) return null

        // 1. 编译通缉令
        val pattern = Pattern.compile(BV_PATTERN)
        // 2. 开始搜查
        val matcher = pattern.matcher(text)

        // 3. 如果找到了，就把那个家伙抓出来
        if (matcher.find()) {
            return matcher.group()
        }

        // 没找到
        return null
    }
}