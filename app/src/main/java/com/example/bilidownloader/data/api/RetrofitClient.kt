package com.example.bilidownloader.data.api

import android.content.Context
import com.example.bilidownloader.utils.CookieManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // 【新增】一个变量来持有 ApplicationContext
    private var appContext: Context? = null

    // 【新增】一个初始化方法，在 Application 类中调用
    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    // ===========================
    // 1. B 站 API 配置
    // ===========================
    private const val BILI_BASE_URL = "https://api.bilibili.com/"

    // 【修改】为 Bili Client 添加拦截器
    private val biliOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                val originalRequest = chain.request()
                val builder = originalRequest.newBuilder()
                    // 固定添加 Referer
                    .header("Referer", "https://www.bilibili.com")
                    // 动态添加 Cookie
                    .apply {
                        appContext?.let { ctx ->
                            CookieManager.getCookie(ctx)?.let { cookie ->
                                header("Cookie", cookie)
                            }
                        }
                    }
                val newRequest = builder.build()
                chain.proceed(newRequest)
            }
            .build()
    }

    private val biliRetrofit = Retrofit.Builder()
        .baseUrl(BILI_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(biliOkHttpClient) // 【修改】使用我们定制的 client
        .build()

    val service: BiliApiService = biliRetrofit.create(BiliApiService::class.java)


    // ===========================
    // 2. 阿里云百炼 API 配置 (这部分保持不变)
    // ===========================
    private const val ALIYUN_BASE_URL = "https://dashscope.aliyuncs.com/"

    private val aliyunRetrofit = Retrofit.Builder()
        .baseUrl(ALIYUN_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(OkHttpClient.Builder().build())
        .build()

    val aliyunService: AliyunApiService = aliyunRetrofit.create(AliyunApiService::class.java)
}