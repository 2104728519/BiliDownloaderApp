package com.example.bilidownloader.core.network.api

import com.example.bilidownloader.core.model.ConsoleResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

/**
 * 阿里云控制台爬虫接口.
 *
 * 模拟浏览器行为，请求控制台的私有 API 以获取 AI 模型调用量数据。
 * 该接口必须伪装 User-Agent 以避免被阿里云风控系统识别为机器人。
 */
interface ConsoleApiService {

    @POST("data/api.json")
    @FormUrlEncoded
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Safari/537.36 Edg/123.0.0.0",
        "Referer: https://bailian.console.aliyun.com/",
        "Origin: https://bailian.console.aliyun.com"
    )
    suspend fun getMonitorData(
        @Header("Cookie") cookie: String,
        @Field("action") action: String = "BroadScopeAspnGateway",
        @Field("product") product: String = "sfm_bailian",
        @Field("api") api: String = "zeldaEasy.bailian-telemetry.monitor.getMonitorDataWithOss",
        @Field("_v") v: String = "undefined",
        @Field("region") region: String = "cn-beijing",
        @Field("sec_token") secToken: String,
        @Field("params") paramsJson: String
    ): ConsoleResponse
}