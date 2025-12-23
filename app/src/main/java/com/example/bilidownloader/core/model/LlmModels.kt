package com.example.bilidownloader.core.model

/**
 * AI 厂商类型枚举.
 */
enum class AiProvider(val label: String) {
    GOOGLE("Google Gemini"),
    DEEPSEEK("DeepSeek"),
    OPENAI("OpenAI (Coming Soon)"),
    ALIYUN("通义千问 (Coming Soon)")
}

/**
 * 领域模型：AI 模型配置.
 *
 * @property id API 调用时使用的模型标识符 (如 "gemini-2.5-flash").
 * @property maxTokenContext 上下文窗口限制，用于判断是否需要截断输入.
 * @property isSmartMode 是否开启智能托管模式 (自动切换模型以节省配额).
 */
data class AiModelConfig(
    val id: String,
    val name: String,
    val provider: AiProvider,
    val maxTokenContext: Int,
    val isSmartMode: Boolean = false
) {
    companion object {
        // --- 智能选项 ---
        val SMART_AUTO = AiModelConfig("auto", "✨ 智能托管 (自动切换)", AiProvider.GOOGLE, 128000, true)

        // --- Google Models ---
        val GEMMA_27B = AiModelConfig("gemma-3-27b-it", "Gemma 3 (27B)", AiProvider.GOOGLE, 8192)
        val GEMINI_FLASH_25 = AiModelConfig("gemini-2.5-flash", "Gemini 2.5 Flash", AiProvider.GOOGLE, 1000000)
        val GEMINI_LITE_25 = AiModelConfig("gemini-2.5-flash-lite", "Gemini 2.5 Lite", AiProvider.GOOGLE, 1000000)

        // --- Gemini 3 Series ---
        val GEMINI3_FLASH = AiModelConfig("gemini-3-flash", "Gemini 3 Flash", AiProvider.GOOGLE, 1000000)
        val GEMINI3_PRO = AiModelConfig("gemini-3-pro", "Gemini 3 Pro", AiProvider.GOOGLE, 1000000)

        // --- DeepSeek Models ---
        val DEEPSEEK_CHAT = AiModelConfig("deepseek-chat", "DeepSeek (Chat)", AiProvider.DEEPSEEK, 128000)
        val DEEPSEEK_REASONER = AiModelConfig("deepseek-reasoner", "DeepSeek (Reasoner)", AiProvider.DEEPSEEK, 128000)

        /** 获取当前支持的所有模型配置列表 */
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