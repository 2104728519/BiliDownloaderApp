package com.example.bilidownloader.data.repository

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.data.service.DeepSeekLlmService // [新增]
import com.example.bilidownloader.data.service.GoogleLlmService
import com.example.bilidownloader.data.service.ILlmService
import com.example.bilidownloader.domain.model.AiModelConfig
import com.example.bilidownloader.domain.model.AiProvider

/**
 * AI 大模型仓库
 * 采用策略模式：根据传入的 AiModelConfig 自动路由到对应的厂商服务
 */
class LlmRepository {

    // 注册所有支持的服务
    private val services: Map<AiProvider, ILlmService> = mapOf(
        AiProvider.GOOGLE to GoogleLlmService(),
        // [新增] 注册 DeepSeek 服务
        AiProvider.DEEPSEEK to DeepSeekLlmService()
    )

    /**
     * 统一生成入口
     * @param modelConfig 指定的模型配置 (包含厂商信息、模型 ID 及是否为智能模式)
     * @param prompt 提示词
     */
    suspend fun generateComment(modelConfig: AiModelConfig, prompt: String): Resource<String> {
        // 1. 根据配置找到对应的服务商实现
        val service = services[modelConfig.provider]
            ?: return Resource.Error("暂不支持该厂商: ${modelConfig.provider.label}")

        // 2. 委托具体的服务商处理生成逻辑
        return service.generate(modelConfig, prompt)
    }
}