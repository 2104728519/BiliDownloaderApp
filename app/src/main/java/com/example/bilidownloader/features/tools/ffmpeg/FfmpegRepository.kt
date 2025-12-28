// 文件位置：features/ffmpeg/FfmpegRepository.kt
package com.example.bilidownloader.features.ffmpeg

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.json.JSONObject // [新增] 用于构建 AI 包装结构
import java.io.File
import java.net.URLDecoder

/**
 * FFmpeg 核心执行仓库 (The Engine).
 */
class FfmpegRepository(private val context: Context) {

    /**
     * [修改] 获取媒体信息，并封装 AI 提示词包装层
     * * 将原始 FFprobe JSON 包装在带有 AI 指令的结构中，方便用户直接复制给 AI 分析。
     */
    suspend fun getMediaInfo(filePath: String): String = withContext(Dispatchers.IO) {
        try {
            // 1. 获取原始 ffprobe 数据
            val command = "-v quiet -print_format json -show_format -show_streams \"$filePath\""
            val session = FFprobeKit.execute(command)

            if (ReturnCode.isSuccess(session.returnCode)) {
                val rawJson = session.output ?: "{}"

                // 2. [核心逻辑] 构建带提示词的包装 JSON
                val instruction = """
                    你是一个专业的 FFmpeg 参数生成助手。请根据提供的 'media_data' 分析媒体流信息（如编码格式、分辨率、码率等），并根据我的需求生成优化后的命令。
                    
                    【严格输出规则】：
                    1. 仅输出参数部分 (Arguments)，不要包含 'ffmpeg'、'-i input' 或输出文件名。
                    2. 必须是单行字符串，禁止换行符 (\)。
                    3. 针对 Android 手机性能优化（默认使用 -preset medium 或 fast）。
                    
                    【输出示例】：
                    -c:v libx264 -crf 23 -c:a aac -b:a 128k
                """.trimIndent()

                // 使用 JSONObject 包装，确保生成的字符串符合标准且结构清晰
                val wrapper = JSONObject()
                // 使用 "0_" 前缀确保在大多数 JSON 排序中靠前显示
                wrapper.put("0_instruction_for_ai", instruction)
                wrapper.put("media_data", JSONObject(rawJson))

                // 返回格式化后的 JSON (缩进 2 空格)，极大地提高了 AI 的阅读准确率
                wrapper.toString(2)
            } else {
                "获取媒体信息失败: ReturnCode=${session.returnCode}\n${session.failStackTrace ?: ""}"
            }
        } catch (e: Exception) {
            "执行 FFprobe 异常: ${e.message}"
        }
    }

    /**
     * 执行自定义 FFmpeg 命令.
     */
    fun executeCommand(
        inputUri: String,
        args: String,
        outputExtension: String
    ): Flow<FfmpegTaskState> = callbackFlow {
        var sessionId: Long? = null
        val logs = mutableListOf<String>()
        val startTime = System.currentTimeMillis()
        var totalDuration = 0L

        val tempInputPath = Uri.parse(inputUri).path?.let {
            URLDecoder.decode(it, "UTF-8")
        } ?: throw IllegalArgumentException("无效的输入路径")

        val outputFileName = "out_${System.currentTimeMillis()}$outputExtension"
        val outputFile = File(context.cacheDir, outputFileName)
        if (outputFile.exists()) outputFile.delete()

        val fullCommand = "-y -i \"$tempInputPath\" $args \"${outputFile.absolutePath}\""

        logs.add(">>> 开始执行命令: $fullCommand")
        trySend(FfmpegTaskState.Running(0f, logs.toList(), 0L, fullCommand))

        val session = FFmpegKit.executeAsync(fullCommand,
            { session ->
                val returnCode = session.returnCode
                val endTime = System.currentTimeMillis()

                if (ReturnCode.isSuccess(returnCode)) {
                    logs.add(">>> 命令执行成功，耗时: ${(endTime - startTime)/1000}s")
                    trySend(FfmpegTaskState.Success(outputFile.absolutePath, logs.toList(), endTime - startTime))
                } else {
                    logs.add(">>> 错误: 退出代码 $returnCode")
                    logs.add(">>> 错误日志: ${session.failStackTrace}")
                    trySend(FfmpegTaskState.Error("FFmpeg 执行失败 (RC=$returnCode)", logs.toList()))
                }
                close()
            },
            { log ->
                logs.add(log.message)
                if (logs.size > 1000) logs.removeAt(0)
                if (totalDuration == 0L && log.message.contains("Duration:")) {
                    totalDuration = parseDuration(log.message)
                }
                trySend(FfmpegTaskState.Running(-1f, logs.toList(), totalDuration, fullCommand))
            },
            { stats ->
                if (totalDuration > 0) {
                    val progress = (stats.time.toLong() / 1000.0 / totalDuration).toFloat().coerceIn(0f, 1f)
                    trySend(FfmpegTaskState.Running(progress, logs.toList(), totalDuration, fullCommand))
                }
            }
        )

        sessionId = session.sessionId
        awaitClose { sessionId?.let { FFmpegKit.cancel(it) } }
    }.flowOn(Dispatchers.IO)

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