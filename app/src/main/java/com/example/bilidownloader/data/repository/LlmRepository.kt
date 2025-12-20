package com.example.bilidownloader.data.repository

import com.example.bilidownloader.BuildConfig
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.data.model.GeminiContent
import com.example.bilidownloader.data.model.GeminiPart
import com.example.bilidownloader.data.model.GeminiRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * LLM 交互仓库
 * 负责调用 Gemini API 生成文本
 */
class LlmRepository {

    private val apiService = NetworkModule.geminiService

    suspend fun generateComment(prompt: String): Resource<String> = withContext(Dispatchers.IO) {
        try {
            // 从 BuildConfig 获取 API Key (需要在 local.properties 配置 GEMINI_API_KEY)
            val apiKey =BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty()) {
                return@withContext Resource.Error("API Key 未配置，请检查 local.properties")
            }

            // 构建请求体
            val request = GeminiRequest(
                contents = listOf(
                    GeminiContent(
                        parts = listOf(GeminiPart(text = prompt))
                    )
                )
            )

            // 调用 API
            val response = apiService.generateContent(apiKey, request)

            // 解析结果
            val candidates = response.candidates
            if (!candidates.isNullOrEmpty()) {
                val text = candidates[0].content?.parts?.get(0)?.text
                if (!text.isNullOrEmpty()) {
                    return@withContext Resource.Success(text.trim())
                }
            }

            Resource.Error("AI 生成了空内容")

        } catch (e: Exception) {
            e.printStackTrace()
            Resource.Error("AI生成失败: ${e.message}")
        }
    }
}