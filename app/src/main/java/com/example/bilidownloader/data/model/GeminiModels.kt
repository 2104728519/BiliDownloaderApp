package com.example.bilidownloader.data.model

// region 1. Request

/**
 * Google Gemini API 请求体.
 */
data class GeminiRequest(
    val contents: List<GeminiContent>,
    // 安全设置：默认全部放行 (BLOCK_NONE) 以避免误杀
    val safetySettings: List<SafetySetting> = defaultSafetySettings(),
    val generationConfig: GeminiConfig = GeminiConfig()
) {
    companion object {
        fun defaultSafetySettings(): List<SafetySetting> {
            return listOf(
                SafetySetting("HARM_CATEGORY_HARASSMENT", "BLOCK_NONE"),
                SafetySetting("HARM_CATEGORY_HATE_SPEECH", "BLOCK_NONE"),
                SafetySetting("HARM_CATEGORY_SEXUALLY_EXPLICIT", "BLOCK_NONE"),
                SafetySetting("HARM_CATEGORY_DANGEROUS_CONTENT", "BLOCK_NONE"),
                SafetySetting("HARM_CATEGORY_CIVIC_INTEGRITY", "BLOCK_NONE")
            )
        }
    }
}

data class SafetySetting(
    val category: String,
    val threshold: String
)

data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiConfig(
    val temperature: Float = 0.8f, // 创造性参数 (0.0~1.0)
    val maxOutputTokens: Int = 2000
)

// endregion

// region 2. Response

data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?,
    // 结束原因：STOP(正常), MAX_TOKENS(超长), SAFETY(安全拦截)
    val finishReason: String?
)

// endregion