package com.example.bilidownloader.core.network

import android.annotation.SuppressLint
import android.content.Context
import com.example.bilidownloader.core.common.Constants
import com.example.bilidownloader.data.api.AliyunApiService
import com.example.bilidownloader.data.api.BiliApiService
import com.example.bilidownloader.data.api.ConsoleApiService
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
            level = HttpLoggingInterceptor.Level.BASIC // 调试时可改为 BODY
        }
    }

    // B 站专用 Client (带 Cookie 处理)
    private val biliOkHttpClient: OkHttpClient by lazy {
        val ctx = getContext()
        OkHttpClient.Builder()
            .addInterceptor(BiliHeaderInterceptor())        // 添加通用头
            .addInterceptor(AuthInterceptor(ctx))           // 注入 Cookie
            .addInterceptor(ReceivedCookieInterceptor(ctx))   // 保存 Cookie
            .addInterceptor(loggingInterceptor)             // 日志
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .build()
    }

    // 阿里云/通用 Client (纯净版)
    private val commonOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 【新增】下载专用 Client
     * 1. 不添加日志拦截器，避免大文件二进制流刷屏导致内存溢出 (OOM)
     * 2. 设置极长的读取超时，防止大文件下载过程中由于网络波动中断
     */
    val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(60, TimeUnit.SECONDS) // 连接超时 1分钟
            .readTimeout(120, TimeUnit.SECONDS)   // 读取超时 2分钟 (之后由业务层重试机制处理)
            .retryOnConnectionFailure(true)       // 允许失败重连
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
            .client(commonOkHttpClient) // 控制台的 Cookie 是通过参数手动传的
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
}