package com.example.bilidownloader.core.util

import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap

/**
 * AI 模型配额与速率限制管理器 (智能调度核心).
 *
 * 负责监控各 AI 模型的 RPM (每分钟请求数) 和 TPM (每分钟 Token 数)，
 * 并根据任务大小动态选择最合适的模型，以实现成本和成功率的最优平衡。
 */
object RateLimitHelper {

    /**
     * 定义支持的模型及其配额限制.
     * 数据基于官方文档，并预留了安全缓冲区.
     */
    enum class AiModel(val apiName: String, val maxRpm: Int, val maxTpm: Int) {
        // 小任务首选：Gemma 27B (高 RPM，适合高频短对话)
        GEMMA_3_27B("gemma-3-27b-it", 28, 14000),

        // 中型任务/主力：Gemini Flash (低 RPM 但超高 TPM，适合处理长视频字幕)
        GEMINI_FLASH("gemini-2.5-flash", 5, 200000),

        // 兜底备用：Flash Lite (折衷方案)
        GEMINI_LITE("gemini-2.5-flash-lite", 9, 200000)
    }

    private const val WINDOW_MS = 60 * 1000L // 统计窗口：1分钟

    // 线程安全的请求历史记录
    private val requestHistory = ConcurrentHashMap<String, LinkedList<Long>>()
    private val tokenHistory = ConcurrentHashMap<String, LinkedList<Pair<Long, Int>>>()

    /**
     * 智能路由策略：选择最佳可用模型.
     *
     * 策略逻辑：
     * 1. 优先使用轻量级模型 (Gemma) 处理短文本 (< 8k Tokens).
     * 2. 若 Gemma 配额耗尽或任务过大，降级至 Gemini Flash.
     * 3. 若 Flash 也不可用，尝试 Lite 版本.
     *
     * @param estimatedTokens 任务预估消耗的 Token 数.
     * @return 推荐的模型枚举，若全部不可用则返回 null.
     */
    @Synchronized
    fun selectBestModel(estimatedTokens: Int): AiModel? {
        cleanOldRecords() // 懒惰清理过期记录

        // 策略 1: 优先 Gemma
        if (estimatedTokens < 8000) {
            if (isModelAvailable(AiModel.GEMMA_3_27B, estimatedTokens)) {
                return AiModel.GEMMA_3_27B
            }
        }

        // 策略 2: 降级至 Gemini Flash
        if (isModelAvailable(AiModel.GEMINI_FLASH, estimatedTokens)) {
            return AiModel.GEMINI_FLASH
        }

        // 策略 3: 尝试 Flash Lite
        if (isModelAvailable(AiModel.GEMINI_LITE, estimatedTokens)) {
            return AiModel.GEMINI_LITE
        }

        return null
    }

    /**
     * 记录一次实际的模型调用，更新配额统计.
     */
    @Synchronized
    fun recordUsage(model: AiModel, tokenCount: Int) {
        val now = System.currentTimeMillis()
        requestHistory.computeIfAbsent(model.name) { LinkedList() }.add(now)
        tokenHistory.computeIfAbsent(model.name) { LinkedList() }.add(now to tokenCount)
    }

    /**
     * 简易 Token 估算器.
     * 经验公式：字符数 / 2.5 + Prompt 基础开销.
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length / 2.5).toInt() + 100
    }

    // region Internal Helpers

    private fun isModelAvailable(model: AiModel, tokens: Int): Boolean {
        // 1. 检查 RPM
        val requests = requestHistory[model.name] ?: LinkedList()
        if (requests.size >= model.maxRpm) return false

        // 2. 检查 TPM
        val usageList = tokenHistory[model.name] ?: LinkedList()
        val currentTpm = usageList.sumOf { it.second }
        if (currentTpm + tokens >= model.maxTpm) return false

        return true
    }

    private fun cleanOldRecords() {
        val now = System.currentTimeMillis()
        val expiry = now - WINDOW_MS

        AiModel.values().forEach { model ->
            // 清理过期请求时间戳
            val requests = requestHistory[model.name]
            while (requests != null && requests.isNotEmpty() && requests.peek()!! < expiry) {
                requests.poll()
            }
            // 清理过期 Token 记录
            val tokens = tokenHistory[model.name]
            while (tokens != null && tokens.isNotEmpty() && tokens.peek()!!.first < expiry) {
                tokens.poll()
            }
        }
    }
    // endregion
}