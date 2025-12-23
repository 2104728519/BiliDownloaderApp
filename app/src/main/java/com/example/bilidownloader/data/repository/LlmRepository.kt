package com.example.bilidownloader.data.repository

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.data.service.DeepSeekLlmService
import com.example.bilidownloader.data.service.GoogleLlmService
import com.example.bilidownloader.data.service.ILlmService
import com.example.bilidownloader.domain.model.AiModelConfig
import com.example.bilidownloader.domain.model.AiProvider

/**
 * LLM (大模型) 聚合仓库.
 *
 * 采用策略模式 (Strategy Pattern) 管理不同的 AI 服务提供商。
 * 根据传入配置动态路由到对应的实现类 (Google Gemini 或 DeepSeek)，
 * 从而屏蔽了不同厂商 API 签名的差异。
 */
class LlmRepository {

    // 服务注册表
    private val services: Map<AiProvider, ILlmService> = mapOf(
        AiProvider.GOOGLE to GoogleLlmService(),
        AiProvider.DEEPSEEK to DeepSeekLlmService()
    )

    /**
     * 统一生成入口.
     * @param modelConfig 模型配置 (包含 provider, modelId 等).
     * @param prompt 提示词.
     */
    suspend fun generateComment(modelConfig: AiModelConfig, prompt: String): Resource<String> {
        val service = services[modelConfig.provider]
            ?: return Resource.Error("暂不支持该厂商: ${modelConfig.provider.label}")

        return service.generate(modelConfig, prompt)
    }
}