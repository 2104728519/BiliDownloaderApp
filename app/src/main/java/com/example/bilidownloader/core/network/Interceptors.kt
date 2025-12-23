package com.example.bilidownloader.core.network

import android.content.Context
import android.util.Log
import com.example.bilidownloader.core.common.Constants
import com.example.bilidownloader.core.manager.CookieManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * B 站防风控请求头拦截器.
 *
 * 负责向请求中注入伪装的 User-Agent, Referer 和 Origin 等头部信息。
 * 这是通过 B 站 API 校验（避免 403 Forbidden）的必要步骤。
 */
class BiliHeaderInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()
            .header("Referer", Constants.BILI_REFERER)
            .header("User-Agent", Constants.COMMON_USER_AGENT)
            .header("Origin", Constants.BILI_REFERER)

        return chain.proceed(builder.build())
    }
}

/**
 * 身份认证拦截器 (Cookie 注入).
 *
 * 从本地 [CookieManager] 读取持久化的 Cookie，并将其添加到 HTTP 请求头的 "Cookie" 字段中。
 * 用于维持用户的登录状态。
 */
class AuthInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()

        // 仅当本地存在有效 Cookie 时才进行注入
        CookieManager.getCookie(context)?.let { cookie ->
            builder.header("Cookie", cookie)
        }

        return chain.proceed(builder.build())
    }
}

/**
 * 响应拦截器 (Cookie 同步).
 *
 * 监听服务器响应中的 "Set-Cookie" 头部，将新的 Cookie 自动同步到本地存储。
 * 确保 Session 能够随服务器下发的更新保持最新。
 */
class ReceivedCookieInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalResponse = chain.proceed(chain.request())

        if (originalResponse.headers("Set-Cookie").isNotEmpty()) {
            val cookies = originalResponse.headers("Set-Cookie")
            Log.d("Network", "收到 Set-Cookie 更新: $cookies")
            CookieManager.saveCookies(context, cookies)
        }

        return originalResponse
    }
}