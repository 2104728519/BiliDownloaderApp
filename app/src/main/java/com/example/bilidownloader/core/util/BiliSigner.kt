package com.example.bilidownloader.core.util

import java.net.URLEncoder
import java.security.MessageDigest
import java.util.TreeMap

/**
 * B 站 WBI (Web Browser Interface) 签名算法工具类.
 *
 * 负责实现 B 站 API 的参数加密校验机制。该机制要求客户端必须对请求参数进行
 * 特定的重排序、拼接，并结合动态获取的 Mixin Key 进行 MD5 签名。
 */
object BiliSigner {

    /**
     * Mixin Key 重组索引表.
     * B 站前端 JS 中定义的固定置换表，用于打乱 imgKey 和 subKey 的字符顺序。
     */
    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52
    )

    /**
     * 计算字符串的 MD5 哈希值 (32位小写 hex).
     */
    private fun md5(str: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(str.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * 生成 Mixin Key (混合密钥).
     *
     * 将从 Nav 接口获取的 imgKey 和 subKey 拼接后，根据 [MIXIN_KEY_ENC_TAB]
     * 进行字符位置重排，生成最终用于签名的密钥。
     */
    fun getMixinKey(imgKey: String, subKey: String): String {
        val raw = imgKey + subKey
        val sb = StringBuilder()

        for (i in 0 until 32) {
            if (i < MIXIN_KEY_ENC_TAB.size) {
                val charIndex = MIXIN_KEY_ENC_TAB[i]
                if (charIndex < raw.length) {
                    sb.append(raw[charIndex])
                }
            }
        }
        return sb.toString()
    }

    /**
     * 对请求参数进行 WBI 签名.
     *
     * 1. 注入当前时间戳 (wts).
     * 2. 对参数按 Key 字典序排序.
     * 3. 过滤非法字符并进行 URL 编码.
     * 4. 拼接 Mixin Key 并计算 MD5.
     *
     * @param params 原始请求参数 (TreeMap 保证有序).
     * @param mixinKey 计算好的混合密钥.
     * @return 包含 w_rid (签名) 和 wts (时间戳) 的完整 Query String.
     */
    fun signParams(params: TreeMap<String, Any>, mixinKey: String): String {
        // 1. 注入时间戳 (秒级)
        params["wts"] = System.currentTimeMillis() / 1000

        // 2. 参数序列化与编码
        val queryBuilder = StringBuilder()
        for ((k, v) in params) {
            if (queryBuilder.isNotEmpty()) {
                queryBuilder.append("&")
            }

            // 过滤特殊字符 (!'()*) 防止干扰签名计算
            val cleanValue = v.toString().filter { it !in "!'()*" }

            val encodedKey = URLEncoder.encode(k, "UTF-8")
            val encodedValue = URLEncoder.encode(cleanValue, "UTF-8")

            queryBuilder.append(encodedKey).append("=").append(encodedValue)
        }

        val queryString = queryBuilder.toString()

        // 3. 计算签名: MD5(QueryString + MixinKey)
        val wRid = md5(queryString + mixinKey)

        return "$queryString&w_rid=$wRid"
    }
}