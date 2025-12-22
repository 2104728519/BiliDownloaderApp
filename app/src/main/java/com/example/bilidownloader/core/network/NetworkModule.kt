package com.example.bilidownloader.core.network

import android.annotation.SuppressLint
import android.content.Context
import com.example.bilidownloader.core.common.Constants
import com.example.bilidownloader.data.api.AliyunApiService
import com.example.bilidownloader.data.api.BiliApiService
import com.example.bilidownloader.data.api.ConsoleApiService
import com.example.bilidownloader.data.api.GeminiApiService
import com.example.bilidownloader.data.api.DeepSeekApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络模块 (单例)
 * 负责组装 OkHttp 和 Retrofit，并提供 ApiService 实例
 */
@SuppressLint("StaticFieldLeak")
object NetworkModule {

    private var context: Context? = null

    fun initialize(appContext: Context) {
        context = appContext.applicationContext
    }

    private fun getContext(): Context {
        return context ?: throw IllegalStateException("NetworkModule 未初始化，请在 Application 中调用 initialize()")
    }

    // ========================================================================
    // 1. HTTP Clients 配置
    // ========================================================================

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    // B 站专用 Client
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

    // 通用 Client (阿里云听悟等使用)
    private val commonOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * [新增] AI 专用客户端：超长超时时间
     * 解决 DeepSeek R1 推理模型思考时间过长或 Gemini 生成长文导致的超时问题
     */
    private val llmOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS) // 连接超时 1 分钟
            .readTimeout(300, TimeUnit.SECONDS)   // [关键] 读取超时 5 分钟
            .writeTimeout(60, TimeUnit.SECONDS)   // 写入超时 1 分钟
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

    private val biliRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.BILI_BASE_URL)
            .client(biliOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val aliyunRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.ALIYUN_BASE_URL)
            .client(commonOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val consoleRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(Constants.ALIYUN_CONSOLE_BASE_URL)
            .client(commonOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // [修改] Gemini AI Retrofit：换用 llmOkHttpClient
    private val geminiRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(llmOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    /**
     * [修改] DeepSeek Retrofit：换用 llmOkHttpClient
     */
    private val deepSeekRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/")
            .client(llmOkHttpClient)
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

    val geminiService: GeminiApiService by lazy {
        geminiRetrofit.create(GeminiApiService::class.java)
    }

    val deepSeekService: DeepSeekApiService by lazy {
        deepSeekRetrofit.create(DeepSeekApiService::class.java)
    }
}