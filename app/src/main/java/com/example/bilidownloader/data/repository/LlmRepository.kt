package com.example.bilidownloader.data.repository

import com.example.bilidownloader.BuildConfig
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.data.model.GeminiContent
import com.example.bilidownloader.data.model.GeminiPart
import com.example.bilidownloader.data.model.GeminiRequest
import com.example.bilidownloader.utils.RateLimitHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class LlmRepository {

    private val apiService = NetworkModule.geminiService

    suspend fun generateComment(prompt: String): Resource<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty()) return@withContext Resource.Error("API Key 未配置")

            // 1. 估算 Token
            val estimatedTokens = RateLimitHelper.estimateTokens(prompt)

            // 2. 智能选择模型 (不再死等，而是立即返回结果)
            val selectedModel = RateLimitHelper.selectBestModel(estimatedTokens)

            if (selectedModel == null) {
                // 如果所有模型都满了，或者 Token 实在太大超过了所有模型的限制
                return@withContext Resource.Error("配额不足或内容过长($estimatedTokens tokens)，今日额度已用尽或需冷却")
            }

            // 3. 准备请求
            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
            )

            // 4. 调用 API (传入选中的模型 apiName)
            val response = apiService.generateContent(
                modelName = selectedModel.apiName,
                apiKey = apiKey,
                request = request
            )

            // 5. 记录实际消耗 (如果成功)
            RateLimitHelper.recordUsage(selectedModel, estimatedTokens)

            val candidates = response.candidates
            if (!candidates.isNullOrEmpty()) {
                val text = candidates[0].content?.parts?.get(0)?.text
                if (!text.isNullOrEmpty()) {
                    return@withContext Resource.Success(text.trim())
                }
            }

            Resource.Error("AI 生成了空内容 (模型: ${selectedModel.apiName})")

        } catch (e: Exception) {
            e.printStackTrace()
            // 6. 专门处理 HTTP 429 (Too Many Requests)
            if (e is HttpException && e.code() == 429) {
                return@withContext Resource.Error("触发 Google 风控 (429)，请稍后")
            }
            Resource.Error("AI 生成异常: ${e.message}")
        }
    }
}