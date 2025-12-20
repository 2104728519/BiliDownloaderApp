package com.example.bilidownloader.utils

import kotlinx.coroutines.delay
import java.util.LinkedList

/**
 * Gemini 免费版配额管理器
 * 限制：
 * 1. 20 RPM (每分钟请求数 < 20)
 * 2. 12,000 TPM (每分钟 Token 数 < 12000)
 */
object RateLimitHelper {

    private const val MAX_RPM = 15      // 限制为 15 (官方20，留5个余量给手动操作或其他)
    private const val MAX_TPM = 10000   // 限制为 10000 (官方12000，留余量)
    private const val WINDOW_MS = 60 * 1000L // 1分钟时间窗口

    // 记录过去1分钟内的请求时间戳
    private val requestTimestamps = LinkedList<Long>()
    // 记录过去1分钟内的 Token 消耗 (时间戳 -> Token数)
    private val tokenUsageHistory = LinkedList<Pair<Long, Int>>()

    /**
     * 检查并等待配额
     * @param estimatedTokens 预计本次请求消耗的 Token 数 (字符数 / 4)
     */
    suspend fun waitForQuota(estimatedTokens: Int) {
        while (true) {
            cleanOldRecords() // 清理1分钟以前的记录

            val currentRpm = requestTimestamps.size
            val currentTpm = tokenUsageHistory.sumOf { it.second }

            // 检查是否超标
            val isRpmSafe = currentRpm < MAX_RPM
            val isTpmSafe = (currentTpm + estimatedTokens) < MAX_TPM

            if (isRpmSafe && isTpmSafe) {
                // 配额充足，记录本次请求并放行
                recordUsage(estimatedTokens)
                break
            } else {
                // 配额不足，等待 5 秒后重试
                // 实际场景可以更智能，比如算出最早过期的记录还要多久
                delay(5000)
            }
        }
    }

    /**
     * 估算字符串的 Token 数
     * 简单算法：中文/英文混合环境下，平均 1 Token ≈ 3~4 字符
     * 我们按保守的 1 Token = 2 字符计算，或者直接用字符数，宁可多算不可少算
     */
    fun estimateTokens(text: String): Int {
        if (text.isEmpty()) return 0
        // Google 官方建议：100 tokens ~= 60-80 words.
        // 简单粗暴估算：长度 / 3
        return (text.length / 3) + 50 // 加上 Prompt 的基础消耗
    }

    private fun recordUsage(tokens: Int) {
        val now = System.currentTimeMillis()
        requestTimestamps.add(now)
        tokenUsageHistory.add(now to tokens)
    }

    private fun cleanOldRecords() {
        val now = System.currentTimeMillis()
        val expiry = now - WINDOW_MS

        // 移除过期的请求记录
        while (requestTimestamps.isNotEmpty() && requestTimestamps.peek()!! < expiry) {
            requestTimestamps.poll()
        }

        // 移除过期的 Token 记录
        while (tokenUsageHistory.isNotEmpty() && tokenUsageHistory.peek()!!.first < expiry) {
            tokenUsageHistory.poll()
        }
    }
}