package com.example.bilidownloader.core.network

import android.annotation.SuppressLint
import android.content.Context
import com.example.bilidownloader.core.common.Constants
import com.example.bilidownloader.data.api.AliyunApiService
import com.example.bilidownloader.data.api.BiliApiService
import com.example.bilidownloader.data.api.ConsoleApiService
import com.example.bilidownloader.data.api.GeminiApiService // 【新增】引用 Gemini 接口
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络模块 (单例)
 * 负责组装 OkHttp 和 Retrofit，并提供 ApiService 实例
 */
@SuppressLint("StaticFieldLeak") // Application Context 安全
object NetworkModule {

    private var context: Context? = null

    /**
     * 初始化方法，必须在 Application onCreate 中调用
     */
    fun initialize(appContext: Context) {
        context = appContext.applicationContext
    }

    private fun getContext(): Context {
        return context ?: throw IllegalStateException("NetworkModule 未初始化，请在 Application 中调用 initialize()")
    }

    // ========================================================================
    // 1. HTTP Clients 配置
    // ========================================================================

    // 通用日志拦截器
    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    // B 站专用 Client (带 Cookie 处理)
    private val biliOkHttpClient: OkHttpClient by lazy {
        val ctx = getContext()
        OkHttpClient.Builder()
            .addInterceptor(BiliHeaderInterceptor())
            .addInterceptor(AuthInterceptor(ctx))
            .addInterceptor(ReceivedCookieInterceptor(ctx))
            .addInterceptor(loggingInterceptor)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    // 阿里云/通用/Gemini Client (纯净版)
    private val commonOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 下载专用 Client
     */
    val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // ========================================================================
    // 2. Retrofit 实例
    // ========================================================================

    // B 站 Retrofit
    private val biliRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.BILI_BASE_URL)
            .client(biliOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // 阿里云听悟 Retrofit
    private val aliyunRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.ALIYUN_BASE_URL)
            .client(commonOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // 阿里云控制台 Retrofit
    private val consoleRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.ALIYUN_CONSOLE_BASE_URL)
            .client(commonOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // 【新增】Gemini AI Retrofit
    private val geminiRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/") // Gemini 官方 Base URL
            .client(commonOkHttpClient) // 复用通用 Client
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // ========================================================================
    // 3. 对外暴露的 API Services
    // ========================================================================

    val biliService: BiliApiService by lazy {
        biliRetrofit.create(BiliApiService::class.java)
    }

    val aliyunService: AliyunApiService by lazy {
        aliyunRetrofit.create(AliyunApiService::class.java)
    }

    val consoleService: ConsoleApiService by lazy {
        consoleRetrofit.create(ConsoleApiService::class.java)
    }

    // 【新增】对外暴露 Gemini Service
    val geminiService: GeminiApiService by lazy {
        geminiRetrofit.create(GeminiApiService::class.java)
    }
}