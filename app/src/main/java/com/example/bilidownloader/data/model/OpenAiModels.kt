package com.example.bilidownloader.data.model

import com.google.gson.annotations.SerializedName

/**
 * OpenAI 兼容格式请求体 (用于 DeepSeek 等).
 */
data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = false,
    val temperature: Float = 1.0f,
    // DeepSeek V3/R1 支持超长 Context，需适当调大
    @SerializedName("max_tokens") val maxTokens: Int = 8192
)

data class OpenAiMessage(
    val role: String, // "system", "user", "assistant"
    val content: String?,
    // DeepSeek R1 专属字段：思维链内容
    @SerializedName("reasoning_content") val reasoningContent: String? = null
)

/**
 * OpenAI 兼容格式响应体.
 */
data class OpenAiResponse(
    val id: String?,
    val choices: List<OpenAiChoice>?,
    val usage: OpenAiUsage?
)

data class OpenAiChoice(
    val index: Int,
    val message: OpenAiMessage?,
    @SerializedName("finish_reason") val finishReason: String?
)

data class OpenAiUsage(
    @SerializedName("prompt_tokens") val promptTokens: Int,
    @SerializedName("completion_tokens") val completionTokens: Int,
    @SerializedName("total_tokens") val totalTokens: Int
)