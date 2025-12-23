package com.example.bilidownloader.data.api

import com.example.bilidownloader.data.model.GeminiRequest
import com.example.bilidownloader.data.model.GeminiResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Google Gemini/Gemma API 接口定义.
 *
 * 使用 Google Generative AI 的 REST API 格式。
 * 支持通过 Path 参数动态切换模型 (如 gemini-2.5-flash, gemma-3-27b).
 */
interface GeminiApiService {

    @POST("v1beta/models/{modelName}:generateContent")
    suspend fun generateContent(
        @Path("modelName") modelName: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}