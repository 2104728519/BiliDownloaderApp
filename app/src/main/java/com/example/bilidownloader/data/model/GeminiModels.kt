package com.example.bilidownloader.data.model

import com.google.gson.annotations.SerializedName

// 1. 请求体
data class GeminiRequest(
    val contents: List<GeminiContent>,

    // [新增] 安全设置：关掉所有过滤
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

// 2. 安全设置数据类
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
    val temperature: Float = 0.8f, // 稍微调高一点，让评论更活泼
    val maxOutputTokens: Int = 2000 // 给足空间，防止物理截断
)

// 3. 响应体
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?,
    val finishReason: String? // 这里会告诉你为什么停止 (STOP, MAX_TOKENS, SAFETY)
)