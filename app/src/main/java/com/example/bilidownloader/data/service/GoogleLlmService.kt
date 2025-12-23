package com.example.bilidownloader.data.service

import com.example.bilidownloader.BuildConfig
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.core.util.RateLimitHelper
import com.example.bilidownloader.data.model.GeminiContent
import com.example.bilidownloader.data.model.GeminiPart
import com.example.bilidownloader.data.model.GeminiRequest
import com.example.bilidownloader.data.model.GeminiConfig
import com.example.bilidownloader.domain.model.AiModelConfig
import com.example.bilidownloader.domain.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.HttpException

/**
 * Google Gemini/Gemma 服务实现类.
 *
 * 核心特性：
 * **智能托管模式 (Smart Mode)**: 集成 [RateLimitHelper]，根据任务 Token 量和 API 配额（RPM/TPM）
 * 自动选择最优模型（Gemma 27B vs Gemini Flash），以实现免费配额的最大化利用。
 */
class GoogleLlmService : ILlmService {

    override val provider: AiProvider = AiProvider.GOOGLE
    private val apiService = NetworkModule.geminiService

    override suspend fun generate(modelConfig: AiModelConfig, prompt: String): Resource<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.GEMINI_API_KEY
            if (apiKey.isEmpty()) return@withContext Resource.Error("Google API Key 未配置")

            // 1. 智能模型选择策略
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

            // 2. 构建请求
            val request = GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                generationConfig = GeminiConfig(
                    temperature = 1.0f,
                    maxOutputTokens = 8192
                )
            )

            // 3. 执行调用
            val response = apiService.generateContent(
                modelName = targetModelId,
                apiKey = apiKey,
                request = request
            )

            // 4. 记录配额消耗 (用于下一次智能决策)
            val usedModelEnum = RateLimitHelper.AiModel.entries.find { it.apiName == targetModelId }
            if (usedModelEnum != null) {
                RateLimitHelper.recordUsage(usedModelEnum, RateLimitHelper.estimateTokens(prompt))
            }

            // 5. 结果解析与安全拦截检查
            val candidates = response.candidates
            if (!candidates.isNullOrEmpty()) {
                val candidate = candidates[0]
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