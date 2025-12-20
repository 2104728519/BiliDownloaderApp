
package com.example.bilidownloader.data.api

import com.example.bilidownloader.data.model.GeminiRequest
import com.example.bilidownloader.data.model.GeminiResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GeminiApiService {

    // [修改] 将模型名称由写死改为动态 Path 参数
    @POST("v1beta/models/{modelName}:generateContent")
    suspend fun generateContent(
        @Path("modelName") modelName: String, // 例如 "gemma-3-27b-it"
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}