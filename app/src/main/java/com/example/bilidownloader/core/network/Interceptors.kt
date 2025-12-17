package com.example.bilidownloader.core.network

import android.content.Context
import android.util.Log
import com.example.bilidownloader.core.common.Constants
import com.example.bilidownloader.core.manager.CookieManager
import okhttp3.Interceptor
import okhttp3.Response

/**
 * 通用请求头拦截器
 * 负责添加 User-Agent, Referer, Origin 等防风控 Header
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
 * 认证拦截器 (Cookie 注入)
 * 负责读取本地存储的 Cookie 并注入到请求头中
 */
class AuthInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val builder = originalRequest.newBuilder()

        // 从 CookieManager 读取 Cookie
        CookieManager.getCookie(context)?.let { cookie ->
            builder.header("Cookie", cookie)
        }

        return chain.proceed(builder.build())
    }
}

/**
 * 响应拦截器 (Cookie 保存)
 * 负责捕获服务器返回的 Set-Cookie 并保存到本地
 */
class ReceivedCookieInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalResponse = chain.proceed(chain.request())

        if (originalResponse.headers("Set-Cookie").isNotEmpty()) {
            val cookies = originalResponse.headers("Set-Cookie")
            Log.d("Network", "收到 Set-Cookie: $cookies")
            CookieManager.saveCookies(context, cookies)
        }

        return originalResponse
    }
}