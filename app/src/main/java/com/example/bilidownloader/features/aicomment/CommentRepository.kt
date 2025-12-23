package com.example.bilidownloader.features.aicomment

import android.content.Context
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.manager.CookieManager
import com.example.bilidownloader.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class CommentRepository(private val context: Context) {

    private val apiService = NetworkModule.biliService

    suspend fun postComment(oid: Long, message: String): Resource<String> = withContext(Dispatchers.IO) {
        // 1. 校验逻辑 (原 UseCase 逻辑)
        if (message.isBlank()) {
            return@withContext Resource.Error("评论内容不能为空")
        }
        if (message.length > 1000) {
            return@withContext Resource.Error("评论字数超出限制 (最大1000字)")
        }

        try {
            // 2. 提取 CSRF Token
            val csrfToken = CookieManager.getCookieValue(context, "bili_jct")

            if (csrfToken.isNullOrEmpty()) {
                return@withContext Resource.Error("未登录或无法获取 CSRF Token，请先在设置页登录")
            }

            // 3. 执行请求
            val response = apiService.addReply(
                oid = oid,
                message = message,
                csrf = csrfToken
            )

            if (response.code == 0) {
                Resource.Success("发送成功")
            } else {
                val errorMsg = when (response.code) {
                    -101 -> "账号未登录"
                    12002 -> "评论区已关闭"
                    12015 -> "需要验证码 (暂不支持)"
                    else -> response.message ?: "发送失败 (code: ${response.code})"
                }
                Resource.Error(errorMsg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Resource.Error("网络错误: ${e.message}")
        }
    }
}