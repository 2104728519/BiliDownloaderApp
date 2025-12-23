package com.example.bilidownloader.domain.usecase

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.model.ConclusionData
import com.example.bilidownloader.data.repository.LlmRepository
import com.example.bilidownloader.domain.model.AiModelConfig
import com.example.bilidownloader.domain.model.CommentStyle

/**
 * 评论生成用例 (Prompt Engineering).
 *
 * 负责构建上下文 (Context) 并生成提示词 (Prompt)，调度 LLM 进行创作。
 * 核心逻辑：
 * 1. **上下文拼接**：将 AI 摘要与字幕全文拼接，最大化利用 LLM 的 Context Window。
 * 2. **Prompt 注入**：将用户选择的风格指令 (如“幽默玩梗”) 嵌入到 System Prompt 中。
 */
class GenerateCommentUseCase(private val llmRepository: LlmRepository) {

    suspend operator fun invoke(
        subtitleData: ConclusionData,
        style: CommentStyle,
        modelConfig: AiModelConfig
    ): Resource<String> {

        // 1. 数据准备
        val sb = StringBuilder()
        val summary = subtitleData.modelResult?.summary
        val subtitles = subtitleData.modelResult?.subtitle?.firstOrNull()?.partSubtitle

        if (subtitles.isNullOrEmpty() && summary.isNullOrEmpty()) {
            return Resource.Error("没有足够的字幕或摘要内容供 AI 参考")
        }

        // 拼接摘要 (High Level Context)
        if (!summary.isNullOrEmpty()) {
            sb.append("【视频AI摘要】\n$summary\n\n")
        }

        // 拼接字幕全文 (Detailed Context)
        if (!subtitles.isNullOrEmpty()) {
            sb.append("【视频字幕全文】\n")
            subtitles.forEach { item ->
                sb.append(item.content).append(" ")
            }
        }

        val content = sb.toString()

        // 2. Prompt 构建
        // 使用 Few-Shot 或指令遵循的方式引导模型
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

        // 3. 调度 LLM
        return llmRepository.generateComment(modelConfig, prompt)
    }
}