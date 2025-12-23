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
 * 网络模块配置对象 (单例).
 *
 * 负责集中管理 OkHttp 客户端和 Retrofit 实例的创建。
 * 针对不同的业务场景（B站 API、阿里云、LLM 推理）配置了不同的超时策略和拦截器。
 */
@SuppressLint("StaticFieldLeak")
object NetworkModule {

    private var context: Context? = null

    /**
     * 初始化网络模块.
     * @param appContext 必须传入 Application Context 以避免内存泄漏.
     */
    fun initialize(appContext: Context) {
        context = appContext.applicationContext
    }

    private fun getContext(): Context {
        return context ?: throw IllegalStateException("NetworkModule 未初始化，请在 Application 中调用 initialize()")
    }

    // region 1. HTTP Clients Configuration

    private val loggingInterceptor by lazy {
        HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
    }

    /**
     * B 站专用客户端.
     * 集成了防风控 Header、Cookie 注入和 Cookie 捕获拦截器.
     */
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

    /**
     * 通用客户端.
     * 用于阿里云听悟、控制台爬虫等常规 API 请求.
     */
    private val commonOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * LLM (大语言模型) 专用客户端.
     *
     * 特别配置了超长的读取超时时间 (10分钟)，以适应 DeepSeek R1 等推理模型
     * 或 Gemini 生成长文本时所需的长时间思考/生成过程，防止 SocketTimeoutException.
     */
    private val llmOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(600, TimeUnit.SECONDS)   // 读取超时设定为 10 分钟
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 文件下载专用客户端.
     * 配置了较长的超时时间并开启连接失败重试.
     */
    val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    // endregion

    // region 2. Retrofit Instances

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

    private val geminiRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://generativelanguage.googleapis.com/")
            .client(llmOkHttpClient) // 使用 LLM 专用客户端
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    private val deepSeekRetrofit: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.deepseek.com/")
            .client(llmOkHttpClient) // 使用 LLM 专用客户端
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }

    // endregion

    // region 3. Exposed API Services

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

    // endregion
}