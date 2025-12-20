package com.example.bilidownloader.data.model

// 请求体
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiConfig = GeminiConfig()
)

// [关键修改] 移除了 role 字段
data class GeminiContent(
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiConfig(
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 1000 // 限制评论长度，节约 token
)

// 响应体 (保持不变)
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?,
    val finishReason: String?
)