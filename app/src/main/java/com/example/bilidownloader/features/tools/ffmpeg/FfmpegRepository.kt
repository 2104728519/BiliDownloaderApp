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
import org.json.JSONObject // 用于构建 AI 包装结构
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
                    你是一个专为 Android 移动端 FFmpeg 工具生成参数的专家助手。
                    请根据下方的 'media_data' 分析媒体流信息，并生成优化的处理参数。

                    【⚠️ 核心架构限制 (绝对红线)】
                    1. 架构为 "单文件输入 -> 内存处理 -> 单文件输出"。
                    2. ❌ 严禁引入外部文件：绝对不要生成 -i watermark.png, -vf subtitles=file.srt。
                    3. ❌ 严禁多文件输出：绝对不要生成 -f segment, -f hls, -map 0:v -map 0:a (多路)。
                    4. ✅ 允许复杂滤镜：可以使用 -filter_complex (或 -lavfi) 进行流的克隆(split)、混合(blend)、堆叠(stack)。

                    【💡 复杂滤镜语法指南 (易错点)】
                    1. 变量命名差异：
                       - 在 scale/crop/overlay 中，请使用 'iw' (输入宽) 和 'ih' (输入高)。
                       - 在 blend/geq 数学表达式中，必须使用 'W' (宽) 和 'H' (高)，严禁使用 'iw'/'ih' 否则会报错。
                    2. 链式语法：
                       - 逗号 ',' 表示顺序执行 (先缩放再裁剪)。
                       - 分号 ';' 表示并行流 (流A做缩放，流B做旋转)。
                       - 必须显式命名流，例如 [v1], [main], [pip]。

                    【🚀 推荐的高级命令示例】
                    1. 左右分屏对比 (左边原色，右边素描):
                       -filter_complex "split[a][b];[b]edgedetect[b_edge];[a]crop=iw/2:ih:0:0[left];[b_edge]crop=iw/2:ih:iw/2:0[right];[left][right]hstack"
                    
                    2. 动态波浪分界线 (数学曲线遮罩):
                       -filter_complex "split[a][b];[b]negate[b_neg];[a][b_neg]blend=all_expr='if(gt(Y, H/2 + H/10 * sin(X/W*4*PI + T*3)), A, B)'"
                    
                    3. 画中画 (PIP):
                       -filter_complex "split[main][pip];[pip]scale=iw/4:-1[pip_small];[main][pip_small]overlay=main_w-overlay_w-20:main_h-overlay_h-20"
                    
                    4. 赛博朋克故障风:
                       -filter_complex "split[a][b];[b]rgbashift=rh=-10:bh=10,noise=alls=20:allf=t+u[glitch];[a][glitch]blend=all_expr='if(gt(sin(T*10),0.8),B,A)'"

                    【📝 输出格式严格要求】
                    1. 仅输出参数字符串 (Arguments)。
                    2. 不要包含 'ffmpeg', '-i input', 'output.mp4'。
                    3. 必须是单行字符串，禁止换行符。
                    4. 默认添加 -preset ultrafast 以优化手机性能。
                    5.生成命令时要用代码块包裹命令
                    【✅ 最终输出示例】
                    -filter_complex "split[v1][v2];[v2]hue=s=0[bw];[v1][bw]hstack" -c:v libx264 -preset ultrafast -c:a copy
                    
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