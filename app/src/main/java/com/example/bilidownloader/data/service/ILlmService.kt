package com.example.bilidownloader.data.service

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.domain.model.AiModelConfig

/**
 * LLM 服务通用接口
 * 所有的 AI 厂商适配器都必须实现此接口
 */
interface ILlmService {
    /**
     * 该服务支持哪个厂商
     */
    val provider: com.example.bilidownloader.domain.model.AiProvider

    /**
     * 生成内容
     * @param modelConfig 选中的模型配置
     * @param prompt 提示词
     */
    suspend fun generate(modelConfig: AiModelConfig, prompt: String): Resource<String>
}