package com.example.bilidownloader.data.service

import com.example.bilidownloader.BuildConfig
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.core.util.RateLimitHelper
import com.example.bilidownloader.data.model.GeminiContent
import com.example.bilidownloader.data.model.GeminiPart
import com.example.bilidownloader.data.model.GeminiRequest
import com.example.bilidownloader.data.model.GeminiConfig // [新增引用]
import com.example.bilidownloader.domain.model.AiModelConfig
import com.example.bilidownloader.domain.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

class GoogleLlmService : ILlmService {

    override val provider: AiProvider = AiProvider.GOOGLE
    private val apiService = NetworkModule.geminiService

    override suspend fun generate(modelConfig: AiModelConfig, prompt: String): Resource<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty()) return@withContext Resource.Error("Google API Key 未配置")

            // 1. 确定要使用的实际模型 ID
            val targetModelId = if (modelConfig.isSmartMode) {
                val estimatedTokens = RateLimitHelper.estimateTokens(prompt)
                val bestModel = RateLimitHelper.selectBestModel(estimatedTokens)

                if (bestModel == null) {
                    return@withContext Resource.Error("今日免费配额已耗尽，或请求过快，请稍后重试")
                }
                bestModel.apiName
            } else {
                modelConfig.id
            }

            // 2. 准备请求 【修改点：显式传入配置】
            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                // 强制指定配置：拉满输出上限，并调整随机性
                generationConfig = GeminiConfig(
                    temperature = 1.0f,      // 适度提高创造性，使评论不那么机械
                    maxOutputTokens = 8192   // 提升至 8K 上限，防止内容截断
                )
            )

            // 3. 发送请求
            val response = apiService.generateContent(
                modelName = targetModelId,
                apiKey = apiKey,
                request = request
            )

            // 4. 成功后记录配额消耗
            val usedModelEnum = RateLimitHelper.AiModel.entries.find { it.apiName == targetModelId }
            if (usedModelEnum != null) {
                RateLimitHelper.recordUsage(usedModelEnum, RateLimitHelper.estimateTokens(prompt))
            }

            // 5. 解析结果并监控停止原因
            val candidates = response.candidates
            if (!candidates.isNullOrEmpty()) {
                val candidate = candidates[0]

                // 打印调试日志：监控拦截原因或截断原因
                println("GoogleAI StopReason: ${candidate.finishReason}")

                val text = candidate.content?.parts?.get(0)?.text
                if (!text.isNullOrEmpty()) {
                    return@withContext Resource.Success(text.trim())
                } else if (candidate.finishReason == "SAFETY") {
                    return@withContext Resource.Error("Google AI 因安全策略拦截了生成内容 (SAFETY)")
                }
            }

            Resource.Error("Google AI 返回空内容 (Reason: ${response.candidates?.firstOrNull()?.finishReason})")

        } catch (e: Exception) {
            e.printStackTrace()
            if (e is HttpException && e.code() == 429) {
                return@withContext Resource.Error("触发 Google 429 风控 (请求过快)")
            }
            Resource.Error("Google服务异常: ${e.message}")
        }
    }
}