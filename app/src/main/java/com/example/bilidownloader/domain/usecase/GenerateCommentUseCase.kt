package com.example.bilidownloader.domain.usecase

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.data.model.ConclusionData
import com.example.bilidownloader.data.repository.LlmRepository
import com.example.bilidownloader.domain.model.CommentStyle

/**
 * 业务逻辑：生成 AI 评论
 * 步骤：
 * 1. 从字幕数据中提取纯文本。
 * 2. 截取文本防止 Token 超限 (Gemini 1.5 Flash 窗口很大，但为了省流和速度，限制在 5000 字符左右)。
 * 3. 拼接 Prompt (包含用户选择的风格)。
 * 4. 调用 LLM。
 */
class GenerateCommentUseCase(private val llmRepository: LlmRepository) {

    suspend operator fun invoke(
        subtitleData: ConclusionData,
        style: CommentStyle
    ): Resource<String> {
        // 1. 提取字幕文本
        val sb = StringBuilder()

        // 优先使用摘要 (token 少且核心)
        val summary = subtitleData.modelResult?.summary
        if (!summary.isNullOrEmpty()) {
            sb.append("视频摘要：$summary\n\n")
        }

        // 拼接字幕内容
        val subtitles = subtitleData.modelResult?.subtitle?.firstOrNull()?.partSubtitle
        if (subtitles.isNullOrEmpty() && summary.isNullOrEmpty()) {
            return Resource.Error("没有足够的字幕内容供 AI 参考")
        }

        if (!subtitles.isNullOrEmpty()) {
            sb.append("视频字幕内容：\n")
            for (item in subtitles) {
                sb.append(item.content).append(" ")
                // 简单的长度限制，防止极端长视频
                if (sb.length > 8000) break
            }
        }

        val content = sb.toString()

        // 2. 构建 Prompt
        val prompt = """
            你是一个哔哩哔哩（B站）的资深用户。请根据以下视频的字幕内容，写一条评论。
            
            【指令要求】
            ${style.promptInstruction}
            
            【注意】
            - 不要只是复述内容，要有观点或情感。
            - 不要输出任何解释性文字（如“好的，这是评论...”），直接输出评论内容。
            - 只有在风格要求包含时间戳时才带时间戳，否则纯文本。
            
            【视频内容】
            $content
        """.trimIndent()

        // 3. 调用 Repository
        return llmRepository.generateComment(prompt)
    }
}