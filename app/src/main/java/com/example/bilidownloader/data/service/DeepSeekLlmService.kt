package com.example.bilidownloader.data.service

import com.example.bilidownloader.BuildConfig
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.core.model.OpenAiMessage
import com.example.bilidownloader.core.model.OpenAiRequest
import com.example.bilidownloader.core.model.AiModelConfig
import com.example.bilidownloader.core.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * DeepSeek AI 服务实现类.
 *
 * 适配 DeepSeek V3 和 R1 模型。
 * 特别针对 R1 推理模型进行了优化：
 * 1. 动态调整 Temperature (推理模型需低温).
 * 2. 捕获并打印思维链 (Chain of Thought) 内容.
 */
class DeepSeekLlmService : ILlmService {

    override val provider: AiProvider = AiProvider.DEEPSEEK
    private val apiService = NetworkModule.deepSeekService

    override suspend fun generate(modelConfig: AiModelConfig, prompt: String): Resource<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.DEEPSEEK_API_KEY
            if (apiKey.isEmpty()) return@withContext Resource.Error("DeepSeek API Key 未配置")

            val messages = listOf(
                OpenAiMessage(role = "user", content = prompt)
            )

            // 参数调优：
            // - R1 (reasoner) 建议 temp=0.6 以保证推理逻辑严密.
            // - V3 (chat) 建议 temp=1.3 以增加创造性.
            val temp = if (modelConfig.id.contains("reasoner")) 0.6f else 1.3f

            val request = OpenAiRequest(
                model = modelConfig.id,
                messages = messages,
                temperature = temp,
                maxTokens = 4096
            )

            val response = apiService.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            val message = response.choices?.firstOrNull()?.message

            // [调试日志] 输出 R1 模型的思维链过程
            if (!message?.reasoningContent.isNullOrEmpty()) {
                println("=== DeepSeek R1 深度思考 (思维链) ===")
                println(message?.reasoningContent)
                println("======================================")
            }

            val content = message?.content

            if (!content.isNullOrEmpty()) {
                return@withContext Resource.Success(content.trim())
            }

            Resource.Error("DeepSeek 返回空内容 (已尝试解析 content)")

        } catch (e: Exception) {
            e.printStackTrace()
            // 错误映射
            if (e.message?.contains("402") == true) {
                return@withContext Resource.Error("DeepSeek 余额不足")
            }
            if (e.message?.contains("429") == true) {
                return@withContext Resource.Error("DeepSeek 请求太频繁 (429)")
            }
            Resource.Error("DeepSeek 异常: ${e.message}")
        }
    }
}