package com.example.bilidownloader.core.util

import java.net.URLEncoder
import java.security.MessageDigest
import java.util.TreeMap

/**
 * 专门负责给 B站 API 进行 WBI 签名加密的工具类
 * 就像是一个精通密码学的管家
 */
object BiliSigner {

    // 【神秘藏宝图】
    // 这是一个固定的数字列表，用来打乱密钥的顺序
    // 只有按照这个顺序拼出来的 MixinKey 才是对的
    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52
    )

    /**
     * 动作1：计算 MD5
     * 就像是给一封信盖个红色的火漆印章，证明这封信没被改过。
     * 输入一段文字，输出一串 32 位的乱码。
     */
    private fun md5(str: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(str.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /**
     * 动作2：生成 MixinKey (混合密钥)
     * 把 B 站给的两个原始暗号 (imgKey, subKey)，按照上面的“藏宝图”重新拼贴。
     */
    fun getMixinKey(imgKey: String, subKey: String): String {
        // 先把两个暗号拼在一起
        val raw = imgKey + subKey
        val sb = StringBuilder()

        // 按照藏宝图的顺序，一个字一个字地挑出来
        for (i in 0 until 32) {
            if (i < MIXIN_KEY_ENC_TAB.size) {
                // 查表，看要取第几个字
                val charIndex = MIXIN_KEY_ENC_TAB[i]
                if (charIndex < raw.length) {
                    sb.append(raw[charIndex])
                }
            }
        }
        return sb.toString()
    }

    /**
     * 动作3：给参数签名 (核心功能)
     * 输入：我们要问 B 站的问题 (params)，比如 "我要看BV12345"
     * 输入：拼贴好的混合密钥 (mixinKey)
     * 输出：加了“加密纸条”的完整问题字符串
     */
    fun signParams(params: TreeMap<String, Any>, mixinKey: String): String {
        // 1. 加上当前时间戳 (防止有人拿以前的旧票混进去)
        // wts = 当前时间的秒数
        params["wts"] = System.currentTimeMillis() / 1000

        // 2. 把所有参数整理成 "key=value" 的格式，并用 "&" 连起来
        // 就像把一堆零散的硬币用绳子串起来
        val queryBuilder = StringBuilder()

        // TreeMap 会自动按字母顺序给参数排序 (a...z)，这是 B 站要求的
        for ((k, v) in params) {
            if (queryBuilder.isNotEmpty()) {
                queryBuilder.append("&")
            }

            // 过滤掉一些特殊符号 (比如 !'()* )，防止干扰
            val cleanValue = v.toString().filter { it !in "!'()*" }

            // 进行 URL 编码 (比如把空格变成 %20，汉字变成乱码)
            val encodedKey = URLEncoder.encode(k, "UTF-8")
            val encodedValue = URLEncoder.encode(cleanValue, "UTF-8")

            queryBuilder.append(encodedKey).append("=").append(encodedValue)
        }

        val queryString = queryBuilder.toString()

        // 3. 最后一步：算 MD5 指纹
        // 公式：MD5( 参数串 + 混合密钥 )
        val wRid = md5(queryString + mixinKey)

        // 返回最终结果：参数串 + 指纹
        return "$queryString&w_rid=$wRid"
    }
}