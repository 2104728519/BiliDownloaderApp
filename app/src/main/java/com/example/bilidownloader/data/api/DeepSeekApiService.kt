package com.example.bilidownloader.data.api

import com.example.bilidownloader.data.model.OpenAiRequest
import com.example.bilidownloader.data.model.OpenAiResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface DeepSeekApiService {

    // DeepSeek 兼容 OpenAI 格式
    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String, // 格式: "Bearer sk-xxx"
        @Body request: OpenAiRequest
    ): OpenAiResponse
}