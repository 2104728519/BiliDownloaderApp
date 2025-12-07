package com.example.bilidownloader.data.api

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // ===========================
    // 1. B 站 API 配置
    // ===========================
    private const val BILI_BASE_URL = "https://api.bilibili.com/"

    private val biliRetrofit = Retrofit.Builder()
        .baseUrl(BILI_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(OkHttpClient.Builder().build())
        .build()

    // 暴露给外部使用的 B 站接口 (MainViewModel 用这个)
    val service: BiliApiService = biliRetrofit.create(BiliApiService::class.java)


    // ===========================
    // 2. 阿里云百炼 API 配置
    // ===========================
    private const val ALIYUN_BASE_URL = "https://dashscope.aliyuncs.com/"

    private val aliyunRetrofit = Retrofit.Builder()
        .baseUrl(ALIYUN_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .client(OkHttpClient.Builder().build())
        .build()

    // 暴露给外部使用的阿里云接口 (TranscriptionViewModel 用这个)
    val aliyunService: AliyunApiService = aliyunRetrofit.create(AliyunApiService::class.java)
}