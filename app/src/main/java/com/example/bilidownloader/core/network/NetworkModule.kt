package com.example.bilidownloader.core.network

import android.annotation.SuppressLint
import android.content.Context
import com.example.bilidownloader.core.common.Constants
import com.example.bilidownloader.core.network.api.AliyunApiService
import com.example.bilidownloader.core.network.api.BiliApiService
import com.example.bilidownloader.core.network.api.ConsoleApiService
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 网络模块配置对象 (单例).
 *
 * 负责集中管理 OkHttp 客户端和 Retrofit 实例的创建。
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
     */
    private val commonOkHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    /**
     * 文件下载专用客户端.
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

    // endregion
}