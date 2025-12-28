// 文件: features/ffmpeg/FfmpegRepository.kt
package com.example.bilidownloader.features.ffmpeg

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import com.example.bilidownloader.core.util.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URLDecoder

/**
 * FFmpeg 核心执行仓库 (The Engine).
 *
 * 职责：
 * 1. 组装命令 (Slot 拼接的底层实现).
 * 2. 调用 FFmpegKit 执行任务.
 * 3. 桥接 Log 和 Statistics 回调到 Kotlin Flow.
 * 4. 处理输出文件的持久化存储.
 */
class FfmpegRepository(private val context: Context) {

    private val TAG = "FfmpegRepository"

    /**
     * 执行自定义 FFmpeg 命令.
     *
     * @param inputUri 用户选择的源文件 URI.
     * @param args 用户输入的参数 (不含 -i, ffmpeg, 和输出路径). 例如: "-c:v libx264 -crf 23".
     * @param outputExtension 输出后缀 (如 ".mp4", ".mp3").
     */
    fun executeCommand(
        inputUri: String,
        args: String,
        outputExtension: String
    ): Flow<FfmpegTaskState> = callbackFlow {
        var sessionId: Long? = null
        val logs = mutableListOf<String>()
        val startTime = System.currentTimeMillis()
        var totalDuration = 0L // 用于计算进度

        // 1. 准备文件路径
        // 将 Content URI 转换为缓存文件路径，因为 FFmpeg 需要真实路径
        val tempInputPath = Uri.parse(inputUri).path?.let {
            // 简单的解码处理，实际项目中最好用 StorageHelper.copyUriToCache 确保万无一失
            // 这里假设传入的是已经在缓存目录的绝对路径 (ViewModel层处理)
            URLDecoder.decode(it, "UTF-8")
        } ?: throw IllegalArgumentException("无效的输入路径")

        val outputFileName = "out_${System.currentTimeMillis()}$outputExtension"
        val outputFile = File(context.cacheDir, outputFileName)
        if (outputFile.exists()) outputFile.delete()

        // 2. 拼接命令 (Slot System 的雏形)
        // 结构: ffmpeg -i [INPUT] [USER_ARGS] [OUTPUT]
        val fullCommand = "-y -i \"$tempInputPath\" $args \"${outputFile.absolutePath}\""

        logs.add(">>> 开始执行命令: $fullCommand")
        trySend(FfmpegTaskState.Running(0f, logs.toList(), 0L, fullCommand))

        // 3. 启动 FFmpegKit 任务
        val session = FFmpegKit.executeAsync(fullCommand,
            { session ->
                // --- 完成回调 ---
                val returnCode = session.returnCode
                val endTime = System.currentTimeMillis()

                if (ReturnCode.isSuccess(returnCode)) {
                    logs.add(">>> 命令执行成功，耗时: ${(endTime - startTime)/1000}s")
                    logs.add(">>> 正在保存到媒体库...")

                    // 异步保存文件
                    // 注意：这里需要切回协程环境或者使用回调，callbackFlow 内部可以直接处理
                    // 但为了简化，我们在 ViewModel 层处理具体的 saveToGallery，这里返回路径即可
                    // 或者在这里同步处理：

                    // 假设 Repository 负责搬运文件：
                    // 这里我们暂时返回临时文件路径，让 ViewModel 或 UseCase 决定存哪
                    // 如果要完全符合现有架构，应该调用 StorageHelper

                    trySend(FfmpegTaskState.Success(outputFile.absolutePath, logs.toList(), endTime - startTime))
                } else {
                    logs.add(">>> 错误: 退出代码 $returnCode")
                    logs.add(">>> 错误日志: ${session.failStackTrace}")
                    trySend(FfmpegTaskState.Error("FFmpeg 执行失败 (RC=$returnCode)", logs.toList()))
                }
                close() // 关闭 Flow
            },
            { log ->
                // --- 日志回调 ---
                logs.add(log.message)
                // 限制日志列表大小，防止内存溢出
                if (logs.size > 1000) logs.removeAt(0)

                // 尝试解析 duration (仅在开始阶段)
                if (totalDuration == 0L && log.message.contains("Duration:")) {
                    totalDuration = parseDuration(log.message)
                }

                // 这里我们发送一个新的 Running 状态，携带最新日志
                // 注意：高频发送可能会导致 UI 抖动，ViewModel 可以做 debounce 处理
                trySend(FfmpegTaskState.Running(-1f, logs.toList(), totalDuration, fullCommand))
            },
            { stats ->
                // --- 统计/进度回调 ---
                if (totalDuration > 0) {
                    val progress = (stats.time.toLong() / 1000.0 / totalDuration).toFloat().coerceIn(0f, 1f)
                    trySend(FfmpegTaskState.Running(progress, logs.toList(), totalDuration, fullCommand))
                }
            }
        )

        sessionId = session.sessionId

        // 当 Flow 被取消时 (例如用户离开页面)，取消 FFmpeg 任务
        awaitClose {
            sessionId?.let { FFmpegKit.cancel(it) }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 辅助解析：从日志中提取 Duration (ms)
     * 格式示例: Duration: 00:03:25.45
     */
    private fun parseDuration(log: String): Long {
        try {
            val pattern = "Duration: (\\d{2}):(\\d{2}):(\\d{2}\\.\\d{2})".toRegex()
            val match = pattern.find(log) ?: return 0L
            val (h, m, s) = match.destructured
            return ((h.toLong() * 3600 + m.toLong() * 60 + s.toDouble()) * 1000).toLong()
        } catch (e: Exception) {
            return 0L
        }
    }
}