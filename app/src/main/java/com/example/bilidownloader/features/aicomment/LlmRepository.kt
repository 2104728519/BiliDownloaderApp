package com.example.bilidownloader.features.aicomment

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.model.ConclusionData
import com.example.bilidownloader.data.service.DeepSeekLlmService
import com.example.bilidownloader.data.service.GoogleLlmService
import com.example.bilidownloader.data.service.ILlmService
import com.example.bilidownloader.domain.model.AiModelConfig
import com.example.bilidownloader.domain.model.AiProvider


import com.example.bilidownloader.domain.model.CommentStyle // 暂时保留 domain model 引用，后续可移

/**
 * LLM (大模型) 聚合仓库.
 *
 * 负责调度不同的 AI 服务提供商，并处理 Prompt 构建逻辑。
 */
class LlmRepository {

    // 服务注册表
    private val services: Map<AiProvider, ILlmService> = mapOf(
        AiProvider.GOOGLE to GoogleLlmService(),
        AiProvider.DEEPSEEK to DeepSeekLlmService()
    )

    /**
     * 生成评论 (包含 Prompt 构建逻辑).
     * 原 GenerateCommentUseCase 的逻辑已合并至此.
     */
    suspend fun generateComment(
        subtitleData: ConclusionData,
        style: CommentStyle,
        modelConfig: AiModelConfig
    ): Resource<String> {
        // 1. 数据准备与上下文拼接
        val sb = StringBuilder()
        val summary = subtitleData.modelResult?.summary
        val subtitles = subtitleData.modelResult?.subtitle?.firstOrNull()?.partSubtitle

        if (subtitles.isNullOrEmpty() && summary.isNullOrEmpty()) {
            return Resource.Error("没有足够的字幕或摘要内容供 AI 参考")
        }

        if (!summary.isNullOrEmpty()) {
            sb.append("【视频AI摘要】\n$summary\n\n")
        }

        if (!subtitles.isNullOrEmpty()) {
            sb.append("【视频字幕全文】\n")
            subtitles.forEach { item ->
                sb.append(item.content).append(" ")
            }
        }

        val content = sb.toString()

        // 2. Prompt 构建
        val prompt = """
            你是一个哔哩哔哩（B站）的资深用户。请根据我提供的视频摘要和字幕全文，写一条评论。
            
            【风格指令】
            ${style.promptInstruction}
            
            【约束条件】
            - 综合摘要和字幕的全部信息来构思评论，不要只看一部分。
            - 严禁输出任何解释性文字（如“好的，这是评论...”），直接输出最终的评论内容。
            - 长度控制在 50-100 字之间，除非风格另有要求。
            
            【视频信息】
            $content
        """.trimIndent()

        // 3. 调度服务
        val service = services[modelConfig.provider]
            ?: return Resource.Error("暂不支持该厂商: ${modelConfig.provider.label}")

        return service.generate(modelConfig, prompt)
    }
}