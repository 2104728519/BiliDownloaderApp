package com.example.bilidownloader.data.service

import com.example.bilidownloader.BuildConfig
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.core.util.RateLimitHelper
import com.example.bilidownloader.data.model.GeminiContent
import com.example.bilidownloader.data.model.GeminiPart
import com.example.bilidownloader.data.model.GeminiRequest
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

            // 2. 准备请求
            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
            )

            // 3. 发送请求
            val response = apiService.generateContent(
                modelName = targetModelId,
                apiKey = apiKey,
                request = request
            )

            // 4. 成功后记录配额消耗
            val usedModelEnum = RateLimitHelper.AiModel.values().find { it.apiName == targetModelId }
            if (usedModelEnum != null) {
                RateLimitHelper.recordUsage(usedModelEnum, RateLimitHelper.estimateTokens(prompt))
            }

            // 5. 解析结果并添加调试日志
            val candidates = response.candidates
            if (!candidates.isNullOrEmpty()) {
                val candidate = candidates[0]

                // [修改] 打印停止原因日志
                // 如果显示 SAFETY，说明内容包含敏感词被和谐
                // 如果显示 MAX_TOKENS，说明内容超长被截断
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