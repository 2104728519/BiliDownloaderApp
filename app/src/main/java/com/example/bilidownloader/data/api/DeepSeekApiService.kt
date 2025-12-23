package com.example.bilidownloader.data.api

import com.example.bilidownloader.data.model.OpenAiRequest
import com.example.bilidownloader.data.model.OpenAiResponse
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

/**
 * DeepSeek AI 接口定义.
 * 兼容 OpenAI 的 Chat Completion 格式.
 */
interface DeepSeekApiService {

    @POST("chat/completions")
    suspend fun createChatCompletion(
        @Header("Authorization") authorization: String, // "Bearer <sk-xxx>"
        @Body request: OpenAiRequest
    ): OpenAiResponse
}