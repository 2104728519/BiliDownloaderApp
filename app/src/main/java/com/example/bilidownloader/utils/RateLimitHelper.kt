package com.example.bilidownloader.utils

import java.util.LinkedList
import java.util.concurrent.ConcurrentHashMap

/**
 * 智能模型配额管理器
 * 负责统计每个模型的用量，并根据任务大小自动分配模型
 */
object RateLimitHelper {

    // 定义支持的模型及其限制 (基于你提供的数据，稍微留了点安全余量)
    enum class AiModel(val apiName: String, val maxRpm: Int, val maxTpm: Int) {
        // 小任务首选：Gemma (RPM高，TPM低)
        GEMMA_3_27B("gemma-3-27b-it", 28, 14000),

        // 大任务/备用：Gemini Flash (RPM低，TPM超高)
        GEMINI_FLASH("gemini-2.5-flash", 5, 200000),

        // 最后的备胎：Flash Lite (RPM适中，TPM超高)
        GEMINI_LITE("gemini-2.5-flash-lite", 9, 200000)
    }

    private const val WINDOW_MS = 60 * 1000L // 1分钟窗口

    // 存储每个模型的请求历史 (ModelName -> List<Timestamp>)
    private val requestHistory = ConcurrentHashMap<String, LinkedList<Long>>()
    // 存储每个模型的Token历史 (ModelName -> List<Pair<Timestamp, TokenCount>>)
    private val tokenHistory = ConcurrentHashMap<String, LinkedList<Pair<Long, Int>>>()

    /**
     * 智能选择最佳模型
     * @param estimatedTokens 预计消耗 Token
     * @return 返回可用的模型对象；如果所有模型都配额不足，返回 null
     */
    @Synchronized
    fun selectBestModel(estimatedTokens: Int): AiModel? {
        cleanOldRecords() // 先清理过期记录

        // 策略 1: 如果 Token 很少 (< 8000)，优先尝试 Gemma (省着用大模型)
        if (estimatedTokens < 8000) {
            if (isModelAvailable(AiModel.GEMMA_3_27B, estimatedTokens)) {
                return AiModel.GEMMA_3_27B
            }
        }

        // 策略 2: 如果 Gemma 扛不住 (Token太多 或 RPM耗尽)，尝试 Gemini Flash
        if (isModelAvailable(AiModel.GEMINI_FLASH, estimatedTokens)) {
            return AiModel.GEMINI_FLASH
        }

        // 策略 3: 如果 Flash 也挂了，尝试 Lite
        if (isModelAvailable(AiModel.GEMINI_LITE, estimatedTokens)) {
            return AiModel.GEMINI_LITE
        }

        // 所有模型都不可用
        return null
    }

    /**
     * 记录一次实际使用
     */
    @Synchronized
    fun recordUsage(model: AiModel, tokenCount: Int) {
        val now = System.currentTimeMillis()

        requestHistory.computeIfAbsent(model.name) { LinkedList() }.add(now)
        tokenHistory.computeIfAbsent(model.name) { LinkedList() }.add(now to tokenCount)
    }

    /**
     * 估算 Token 数 (简单版：字符数 / 2.5)
     * 中文比较占 Token，Gemma 的 Tokenizer 对中文处理效率不如 Gemini
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        return (text.length / 2.5).toInt() + 100 // 加上 Prompt 的基础消耗
    }

    // --- 内部辅助方法 ---

    private fun isModelAvailable(model: AiModel, tokens: Int): Boolean {
        val now = System.currentTimeMillis()

        // 1. 检查 RPM (每分钟请求数)
        val requests = requestHistory[model.name] ?: LinkedList()
        if (requests.size >= model.maxRpm) return false

        // 2. 检查 TPM (每分钟 Token 数)
        val usageList = tokenHistory[model.name] ?: LinkedList()
        val currentTpm = usageList.sumOf { it.second }

        if (currentTpm + tokens >= model.maxTpm) return false

        return true
    }

    private fun cleanOldRecords() {
        val now = System.currentTimeMillis()
        val expiry = now - WINDOW_MS

        AiModel.values().forEach { model ->
            // 清理请求记录
            val requests = requestHistory[model.name]
            while (requests != null && requests.isNotEmpty() && requests.peek()!! < expiry) {
                requests.poll()
            }

            // 清理 Token 记录
            val tokens = tokenHistory[model.name]
            while (tokens != null && tokens.isNotEmpty() && tokens.peek()!!.first < expiry) {
                tokens.poll()
            }
        }
    }
}