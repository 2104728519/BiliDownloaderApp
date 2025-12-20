package com.example.bilidownloader.data.api

import com.example.bilidownloader.data.model.GeminiRequest
import com.example.bilidownloader.data.model.GeminiResponse
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Query

interface GeminiApiService {

    // 这里使用动态 URL path 或者在 BaseUrl 中指定，此处采用 Query 参数传递 Key
    @POST("v1beta/models/gemini-2.5-flash:generateContent")
    suspend fun generateContent(
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): GeminiResponse
}