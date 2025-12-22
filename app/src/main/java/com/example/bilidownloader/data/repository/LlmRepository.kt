package com.example.bilidownloader.data.repository

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.data.service.GoogleLlmService
import com.example.bilidownloader.data.service.ILlmService
import com.example.bilidownloader.domain.model.AiModelConfig
import com.example.bilidownloader.domain.model.AiProvider

class LlmRepository {

    // 注册所有支持的服务
    private val services: Map<AiProvider, ILlmService> = mapOf(
        AiProvider.GOOGLE to GoogleLlmService()
        // 未来可以在这里加: AiProvider.DEEPSEEK to DeepSeekService()
    )

    /**
     * 统一生成入口
     * @param modelConfig 指定的模型配置 (包含厂商信息)
     * @param prompt 提示词
     */
    suspend fun generateComment(modelConfig: AiModelConfig, prompt: String): Resource<String> {
        // 1. 根据配置找到对应的服务商
        val service = services[modelConfig.provider]
            ?: return Resource.Error("暂不支持该厂商: ${modelConfig.provider.label}")

        // 2. 委托服务商处理
        return service.generate(modelConfig, prompt)
    }
}