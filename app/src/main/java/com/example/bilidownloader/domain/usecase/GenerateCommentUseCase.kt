package com.example.bilidownloader.domain.usecase

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.data.model.ConclusionData
import com.example.bilidownloader.data.repository.LlmRepository
import com.example.bilidownloader.domain.model.CommentStyle

/**
 * 业务逻辑：生成 AI 评论 (优化版)
 * 步骤：
 * 1. 将视频摘要和字幕全文拼接在一起，为 AI 提供最完整的上下文。
 * 2. 构建优化的 Prompt，引导 AI 综合所有信息进行创作。
 * 3. 调用 LLM。
 */
class GenerateCommentUseCase(private val llmRepository: LlmRepository) {

    suspend operator fun invoke(
        subtitleData: ConclusionData,
        style: CommentStyle
    ): Resource<String> {
        // 1. 提取并拼接所有可用文本
        val sb = StringBuilder()
        val summary = subtitleData.modelResult?.summary
        val subtitles = subtitleData.modelResult?.subtitle?.firstOrNull()?.partSubtitle

        // 如果两者都为空，则无法生成
        if (subtitles.isNullOrEmpty() && summary.isNullOrEmpty()) {
            return Resource.Error("没有足够的字幕或摘要内容供 AI 参考")
        }

        // [修改 1] 将摘要和字幕全部拼接，不设长度限制
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

        // 2. 构建优化的 Prompt
        // [修改 2] 引导 AI 综合利用所有信息
        val prompt = """
            
            ${style.promptInstruction}
            
            【注意】
            - 不要输出任何解释性文字（如“好的，这是评论...”），直接输出最终的评论内容。
            
            【视频信息】
            $content
        """.trimIndent()

        // 3. 调用 Repository
        return llmRepository.generateComment(prompt)
    }
}