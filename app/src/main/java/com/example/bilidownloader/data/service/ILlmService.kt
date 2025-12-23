package com.example.bilidownloader.data.service

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.domain.model.AiModelConfig
import com.example.bilidownloader.domain.model.AiProvider

/**
 * LLM 服务标准接口.
 * 所有 AI 厂商适配器 (Adapter) 必须实现此接口，以支持 Repository 层的策略模式调度。
 */
interface ILlmService {
    /** 支持的厂商标识 */
    val provider: AiProvider

    /**
     * 执行生成任务.
     * @param modelConfig 包含具体的模型 ID 和策略配置.
     */
    suspend fun generate(modelConfig: AiModelConfig, prompt: String): Resource<String>
}