package com.example.bilidownloader.data.api

import android.content.Context
import android.util.Log
import com.example.bilidownloader.utils.CookieManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // 【新增/修改】第一步：定义统一的 User-Agent (Windows 10 Chrome)
    const val COMMON_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    private var appContext: Context? = null

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    private const val BILI_BASE_URL = "https://api.bilibili.com/"

    private val biliOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            // 1. 请求拦截器：自动添加 Cookie 和 Header
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val builder = originalRequest.newBuilder()
                    .header("Referer", "https://www.bilibili.com")
                    .header("User-Agent", COMMON_USER_AGENT) // 【修改】使用常量 COMMON_USER_AGENT
                    .header("Origin", "https://www.bilibili.com") // 【新增】Origin 头，防跨域检查
                    .apply {
                        appContext?.let { ctx ->
                            CookieManager.getCookie(ctx)?.let { cookie ->
                                header("Cookie", cookie)
                            }
                        }
                    }

                val request = builder.build()

                // 打印日志方便检查
                Log.d("RetrofitClient", "发送请求: ${request.url}")
                Log.d("RetrofitClient", "UA: ${request.header("User-Agent")}") // 【检查点 C】日志更新为新格式

                chain.proceed(request)
            }
            // 2. 响应拦截器：自动保存服务器下发的 Cookie (buvid3, b_nut 等)
            .addInterceptor { chain ->
                val originalResponse = chain.proceed(chain.request())

                if (originalResponse.headers("Set-Cookie").isNotEmpty()) {
                    val cookies = originalResponse.headers("Set-Cookie")
                    appContext?.let { ctx ->
                        Log.d("RetrofitClient", "收到 Set-Cookie: $cookies")
                        CookieManager.saveCookies(ctx, cookies)
                    }
                }
                originalResponse
            }
            .build()
    }

    private val biliRetrofit = Retrofit.Builder()
        .baseUrl(BILI_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(biliOkHttpClient)
        .build()

    val service: BiliApiService = biliRetrofit.create(BiliApiService::class.java)

    // 阿里云部分保持不变
    private const val ALIYUN_BASE_URL = "https://dashscope.aliyuncs.com/"
    private val aliyunRetrofit = Retrofit.Builder()
        .baseUrl(ALIYUN_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(OkHttpClient.Builder().build())
        .build()
    val aliyunService: AliyunApiService = aliyunRetrofit.create(AliyunApiService::class.java)
}