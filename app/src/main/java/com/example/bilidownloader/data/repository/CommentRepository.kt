package com.example.bilidownloader.data.repository

import android.content.Context
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.manager.CookieManager
import com.example.bilidownloader.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 评论操作仓库
 * 负责调用 B 站接口发送评论，自动处理 CSRF Token
 */
class CommentRepository(private val context: Context) {

    private val apiService = NetworkModule.biliService

    suspend fun postComment(oid: Long, message: String): Resource<String> = withContext(Dispatchers.IO) {
        try {
            // 1. 获取 CSRF Token (bili_jct)
            // 注意：CookieManager.getCookieValue 需要传入 key="bili_jct"
            val csrfToken = CookieManager.getCookieValue(context, "bili_jct")

            if (csrfToken.isNullOrEmpty()) {
                return@withContext Resource.Error("未登录或无法获取 CSRF Token，请先在设置页登录")
            }

            // 2. 调用接口
            val response = apiService.addReply(
                oid = oid,
                message = message,
                csrf = csrfToken
            )

            // 3. 处理结果
            if (response.code == 0) {
                Resource.Success("发送成功")
            } else {
                // 处理常见错误码 (如 -101 未登录, 12002 评论区关闭)
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