package com.example.bilidownloader.domain.model

/**
 * AI 厂商枚举
 */
enum class AiProvider(val label: String) {
    GOOGLE("Google Gemini"),
    DEEPSEEK("DeepSeek"),
    OPENAI("OpenAI (Coming Soon)"),
    ALIYUN("通义千问 (Coming Soon)")
}

/**
 * 模型定义
 * @param id API 调用时使用的模型 ID (如 "gemini-3-flash", "deepseek-chat")
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
        val GEMINI_FLASH_25 = AiModelConfig("gemini-2.5-flash", "Gemini 2.5 Flash", AiProvider.GOOGLE, 1000000)
        val GEMINI_LITE_25 = AiModelConfig("gemini-2.5-flash-lite", "Gemini 2.5 Lite", AiProvider.GOOGLE, 1000000)

        // --- [新增] Gemini 3 系列 ---
        val GEMINI3_FLASH = AiModelConfig("gemini-3-flash", "Gemini 3 Flash", AiProvider.GOOGLE, 1000000)
        val GEMINI3_PRO = AiModelConfig("gemini-3-pro", "Gemini 3 Pro", AiProvider.GOOGLE, 1000000)

        // --- DeepSeek 模型 ---
        val DEEPSEEK_CHAT = AiModelConfig("deepseek-chat", "DeepSeek (Chat)", AiProvider.DEEPSEEK, 128000)
        val DEEPSEEK_REASONER = AiModelConfig("deepseek-reasoner", "DeepSeek (Reasoner)", AiProvider.DEEPSEEK, 128000)

        // 获取所有可用选项
        fun getAllModels(): List<AiModelConfig> {
            return listOf(
                SMART_AUTO,
                GEMMA_27B,
                GEMINI_FLASH_25,
                GEMINI_LITE_25,
                GEMINI3_FLASH,
                GEMINI3_PRO,
                DEEPSEEK_CHAT,
                DEEPSEEK_REASONER
            )
        }
    }
}
