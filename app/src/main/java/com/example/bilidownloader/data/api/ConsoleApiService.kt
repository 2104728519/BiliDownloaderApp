package com.example.bilidownloader.data.api

import com.example.bilidownloader.data.model.ConsoleResponse
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.POST

interface ConsoleApiService {

    @POST("data/api.json")
    @FormUrlEncoded
    @Headers(
        // 伪装成 Edge 浏览器，避免被风控拦截
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
        @Field("sec_token") secToken: String, // 必须从页面获取
        @Field("params") paramsJson: String   // 复杂的 JSON 参数
    ): ConsoleResponse
}