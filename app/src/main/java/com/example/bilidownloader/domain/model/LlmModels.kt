package com.example.bilidownloader.domain.model

/**
 * AI 厂商枚举
 */
enum class AiProvider(val label: String) {
    GOOGLE("Google Gemini"),
    DEEPSEEK("DeepSeek (Coming Soon)"),
    OPENAI("OpenAI (Coming Soon)"),
    ALIYUN("通义千问 (Coming Soon)")
}

/**
 * 模型定义
 * @param id API 调用时使用的模型 ID (如 "gemini-2.5-flash", "deepseek-chat")
 * @param name UI 显示的名称
 * @param provider 所属厂商
 * @param maxTokenContext 上下文窗口限制 (用于后续截断判断)
 * @param isSmartMode 是否是本地的“智能托管模式”
 */
data class AiModelConfig(
    val id: String,
    val name: String,
    val provider: AiProvider,
    val maxTokenContext: Int,
    val isSmartMode: Boolean = false // 特殊标记：如果为 true，代表交给 RateLimitHelper 自动决策
) {
    // 预定义支持的模型列表
    companion object {
        // --- 特殊选项 ---
        val SMART_AUTO = AiModelConfig("auto", "✨ 智能托管 (自动切换)", AiProvider.GOOGLE, 128000, true)

        // --- Google Models ---
        val GEMMA_27B = AiModelConfig("gemma-3-27b-it", "Gemma 3 (27B)", AiProvider.GOOGLE, 8192)
        val GEMINI_FLASH = AiModelConfig("gemini-2.5-flash", "Gemini 2.5 Flash", AiProvider.GOOGLE, 1000000)
        val GEMINI_LITE = AiModelConfig("gemini-2.5-flash-lite", "Gemini 2.5 Lite", AiProvider.GOOGLE, 1000000)


        // --- Future Models (预埋) ---
        // val DEEPSEEK_V3 = AiModelConfig("deepseek-chat", "DeepSeek V3", AiProvider.DEEPSEEK, 64000)

        // 获取所有可用选项
        fun getAllModels(): List<AiModelConfig> {
            return listOf(SMART_AUTO, GEMMA_27B, GEMINI_FLASH, GEMINI_LITE)
        }
    }
}