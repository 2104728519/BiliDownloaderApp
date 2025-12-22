package com.example.bilidownloader.data.model

import com.google.gson.annotations.SerializedName

// --- 请求体 ---
data class OpenAiRequest(
    val model: String,
    val messages: List<OpenAiMessage>,
    val stream: Boolean = false,
    val temperature: Float = 1.0f,
    // DeepSeek V3 最大输出 8K，R1 推理模型建议设置较大的 max_tokens
    @SerializedName("max_tokens") val maxTokens: Int = 8192
)

// --- 消息实体 ---
data class OpenAiMessage(
    val role: String, // "system", "user", "assistant"
    val content: String?, // 设为可空，因为 R1 可能只返回推理内容而不返回 content

    // [新增] DeepSeek R1 专属：思维链内容
    // 只有在使用 r1 模型时，该字段才会有值
    @SerializedName("reasoning_content") val reasoningContent: String? = null
)

// --- 响应体 ---
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