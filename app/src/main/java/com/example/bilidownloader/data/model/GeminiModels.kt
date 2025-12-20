package com.example.bilidownloader.data.model

import com.google.gson.annotations.SerializedName

// 请求体
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiConfig = GeminiConfig()
)

data class GeminiContent(
    val role: String = "user",
    val parts: List<GeminiPart>
)

data class GeminiPart(
    val text: String
)

data class GeminiConfig(
    val temperature: Float = 0.7f,
    val maxOutputTokens: Int = 200
)

// 响应体
data class GeminiResponse(
    val candidates: List<GeminiCandidate>?
)

data class GeminiCandidate(
    val content: GeminiContent?,
    val finishReason: String?
)