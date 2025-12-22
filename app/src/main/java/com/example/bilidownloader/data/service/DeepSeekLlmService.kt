package com.example.bilidownloader.data.service

import com.example.bilidownloader.BuildConfig
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.data.model.OpenAiMessage
import com.example.bilidownloader.data.model.OpenAiRequest
import com.example.bilidownloader.domain.model.AiModelConfig
import com.example.bilidownloader.domain.model.AiProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class DeepSeekLlmService : ILlmService {

    override val provider: AiProvider = AiProvider.DEEPSEEK
    private val apiService = NetworkModule.deepSeekService

    override suspend fun generate(modelConfig: AiModelConfig, prompt: String): Resource<String> = withContext(Dispatchers.IO) {
        try {
            val apiKey = BuildConfig.DEEPSEEK_API_KEY
            if (apiKey.isEmpty()) return@withContext Resource.Error("DeepSeek API Key 未配置")

            // 1. 构建请求
            val messages = listOf(
                OpenAiMessage(role = "user", content = prompt)
            )

            // 针对 DeepSeek R1 (推理模型) 官方建议温度设为 0.6，V3 建议 1.0-1.3
            val temp = if (modelConfig.id.contains("reasoner")) 0.6f else 1.3f

            val request = OpenAiRequest(
                model = modelConfig.id,
                messages = messages,
                temperature = temp,
                maxTokens = 4096
            )

            // 2. 调用 API
            val response = apiService.createChatCompletion(
                authorization = "Bearer $apiKey",
                request = request
            )

            // 3. 解析结果
            val message = response.choices?.firstOrNull()?.message

            // [新增] 打印 DeepSeek R1 的思考过程 (思维链)
            if (!message?.reasoningContent.isNullOrEmpty()) {
                println("=== DeepSeek R1 深度思考 (思维链) ===")
                println(message?.reasoningContent)
                println("======================================")
            }

            val content = message?.content

            if (!content.isNullOrEmpty()) {
                return@withContext Resource.Success(content.trim())
            }

            // 特殊情况处理：如果 R1 思考了很久但最终 content 为空（可能被过滤或未命中输出）
            Resource.Error("DeepSeek 返回空内容 (已尝试解析 content)")

        } catch (e: Exception) {
            e.printStackTrace()
            // 简单处理 HTTP 错误
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