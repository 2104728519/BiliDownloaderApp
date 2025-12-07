package com.example.bilidownloader.data.api

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * 这里是“快递办事处”
 * 负责制造出可以在上面干活的“快递员”
 */
object RetrofitClient {

    // B 站 API 的大本营地址
    private const val BASE_URL = "https://api.bilibili.com/"

    // 创建一个 Retrofit 实例
    // 就像是装修好了办事处，配好了电话线（BaseUrl）和翻译机（Gson）
    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create()) // 自动把 JSON 转成 Kotlin 对象
        .build()

    // 创建一个可以直接调用的服务实例
    // 以后我们要发请求，直接调用 RetrofitClient.service.xxx() 就可以了
    val service: BiliApiService = retrofit.create(BiliApiService::class.java)
}