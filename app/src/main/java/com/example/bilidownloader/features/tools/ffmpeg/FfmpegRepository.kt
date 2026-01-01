package com.example.bilidownloader.features.ffmpeg

import android.content.Context
import android.net.Uri
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.net.URLDecoder

/**
 * FFmpeg 核心执行仓库 (The Engine).
 * * 职责：
 * 1. 执行 FFmpeg 指令并流式返回进度。
 * 2. 使用 FFprobe 预检媒体信息。
 * 3. [新增] 远程获取文本配置。
 */
class FfmpegRepository(private val context: Context) {

    private val TAG = "FfmpegRepo"

    // 初始化 OkHttpClient (建议在正式项目中通过 Dagger/Koin 注入)
    private val httpClient = OkHttpClient.Builder()
        .followRedirects(true)
        .build()

    /**
     * [新增] 从指定 URL 下载文本内容 (如预设指令、Prompt 等).
     * @param url 远程地址
     * @return 文本字符串
     */
    suspend fun fetchTextFromUrl(url: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Android)")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Exception("网络请求失败: HTTP ${response.code}")

            response.body?.string() ?: throw Exception("响应正文为空")
        }
    }

    /**
     * 执行 FFmpeg 命令并监听实时进度
     */
    fun executeCommand(
        inputUri: String,
        args: String,
        outputExtension: String
    ): Flow<FfmpegTaskState> = callbackFlow {
        var sessionId: Long? = null
        val logs = mutableListOf<String>()
        val startTime = System.currentTimeMillis()

        var currentProgress = 0f

        // 1. 准备路径逻辑
        val tempInputPath = Uri.parse(inputUri).path?.let {
            URLDecoder.decode(it, "UTF-8")
        } ?: throw IllegalArgumentException("无效的输入路径")

        val outputFileName = "out_${System.currentTimeMillis()}$outputExtension"
        val outputFile = File(context.cacheDir, outputFileName)
        if (outputFile.exists()) outputFile.delete()

        // 2. 主动获取总时长 (Proactive Duration Check)
        var totalDuration = 0L
        try {
            val mediaInfo = FFprobeKit.getMediaInformation(tempInputPath)
            val durationStr = mediaInfo.mediaInformation.duration
            if (!durationStr.isNullOrEmpty()) {
                totalDuration = (durationStr.toDouble() * 1000).toLong()
                Log.d(TAG, "✅ FFprobe 预先获取时长: $totalDuration ms")
            }
        } catch (e: Exception) {
            Log.e(TAG, "FFprobe 获取时长失败: ${e.message}")
        }

        // 3. 拼接命令
        val fullCommand = "-y -i \"$tempInputPath\" $args \"${outputFile.absolutePath}\""
        logs.add(">>> 开始执行命令: $fullCommand")

        trySend(FfmpegTaskState.Running(0f, logs.toList(), totalDuration, fullCommand))

        // 4. 异步执行
        val session = FFmpegKit.executeAsync(fullCommand,
            { session ->
                val returnCode = session.returnCode
                val endTime = System.currentTimeMillis()

                if (ReturnCode.isSuccess(returnCode)) {
                    logs.add(">>> 成功，耗时: ${(endTime - startTime)/1000}s")
                    trySend(FfmpegTaskState.Success(outputFile.absolutePath, logs.toList(), endTime - startTime))
                } else {
                    logs.add(">>> 错误: RC=$returnCode")
                    logs.add(">>> 堆栈: ${session.failStackTrace}")
                    trySend(FfmpegTaskState.Error("FFmpeg 失败 (RC=$returnCode)", logs.toList()))
                }
                close()
            },
            { log ->
                logs.add(log.message)
                if (logs.size > 1000) logs.removeAt(0)

                if (totalDuration == 0L) {
                    val tempDuration = parseDuration(log.message)
                    if (tempDuration > 0) {
                        totalDuration = tempDuration
                    }
                }
                trySend(FfmpegTaskState.Running(currentProgress, logs.toList(), totalDuration, fullCommand))
            },
            { stats ->
                if (totalDuration > 0) {
                    val timeMs = stats.time.toDouble()
                    val rawProgress = (timeMs / totalDuration.toDouble()).toFloat()
                    if (rawProgress > currentProgress) {
                        currentProgress = rawProgress.coerceIn(0f, 1f)
                    }
                    trySend(FfmpegTaskState.Running(currentProgress, logs.toList(), totalDuration, fullCommand))
                }
            }
        )

        sessionId = session.sessionId

        awaitClose {
            sessionId?.let { FFmpegKit.cancel(it) }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 获取媒体详细 JSON 信息并注入 AI 指令
     */
    suspend fun getMediaInfo(filePath: String): String = withContext(Dispatchers.IO) {
        try {
            val command = "-v quiet -print_format json -show_format -show_streams \"$filePath\""
            val session = FFprobeKit.execute(command)

            if (ReturnCode.isSuccess(session.returnCode)) {
                val rawJson = session.output
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
                    3.稳定性优先：在 filter_complex 中涉及时间动画时，请优先使用帧数变量 n 而非时间变量 T（例如 sin(n/10)）。在 FFmpeg 中，变量 n 和 t（时间）在 overlay、drawtext 或 geq 滤镜中是可用的，但在 blend 滤镜的表达式中是不支持的
                        变量规范：在 geq/blend 表达式中务必使用 W/H；在 overlay/scale 中务必使用 iw/ih。
                        遮罩逻辑：实现复杂形状切割请使用 geq 生成黑白遮罩，配合 maskedmerge 滤镜进行三路融合。
                        geq 滤镜如果没有指定输入源，它会尝试创建一个新的流，但在这种三路并行（原视频、反色视频、遮罩）的结构中，不指定输入的 geq 往往会导致 FilterGraph 无法正确挂载到时间线上。此外，geq 默认需要处理色彩分量，我们必须明确指定只处理亮度 lum。
                        健壮性：在所有复杂分支后添加 format=yuv420p 以确保 Android 端兼容性。
                        流管理：严禁重复使用已消耗的流标签，请根据需求准确使用 split 滤镜。
                    4.在 Android 端（特别是 FFmpeg Kit 环境下）：
                        线程膨胀：-filter_complex 为了保证多路流的实时性，会开启更复杂的内部调度线程。在处理高清视频时，这些线程会竞态申请连续的大块内存。
                        上下文开销：filter_complex 需要维护一个完整的 FilterGraph 结构，对于单纯想在同一个视频上做画中画的操作来说，它的“行政开销”太大了。
                        内存管理：当使用 -vf 里的 split 时，FFmpeg 往往能复用同一个 Buffer 引用；而在 -filter_complex 中，系统倾向于做深拷贝以保证分支独立性，这直接导致了内存溢出。
                    5.“Format Cleaning (清洗格式) -> Low-Res Calc (低清计算) -> Scale2Ref (对齐) -> Alphamerge (透明融合) -> Overlay (叠加)” 是在 Android 上实现复杂数学特效的唯一真理。遵循此路径可确保 100% 稳定性。
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
                    4.生成命令时要用代码块包裹命令
                    5.强制使用 “Alphamerge + Overlay” 架构作为遮罩合成的唯一标准方案：
geq 生成黑白遮罩。
alphamerge 将遮罩应用到前景流的 Alpha 通道。
overlay 将前景叠加到背景上。
禁止过度复杂的 FilterGraph 级联（如多层 blend 嵌套），应尽量简化为“生成 -> 叠加”的线性逻辑。
2. 数学滤镜安全 (GEQ Safety)
格式清洗：在使用 geq 滤镜之前，必须强制指定像素格式。
✅ 正确：...format=yuv420p,geq=lum='...':cb=128:cr=128...
❌ 错误：直接连接 scale 后使用 geq。
色度保护：在 geq 参数中必须显式设置 cb=128:cr=128（即色度置灰），防止色度通道未初始化导致的绿屏或闪退。
分辨率降级：复杂数学运算（如 hypot, sin, cos）严禁在 1080p+ 原分辨率下进行。
必须先 scale=320:-1 (或更小) 进行计算。
计算完成后再 scale2ref 拉伸回原分辨率。
禁止使用 and(), or() 函数（FFmpeg 解析器不支持）。
✅ 替代：使用乘法 * 代表 AND，加法 + 代表 OR。
例：if(gt(A,B)*lt(C,D), ...)。
坐标归一化：尽量避免直接使用 X 和 Y 进行绝对坐标计算，推荐使用相对于宽高的比例。
推荐写法：hypot(X-W*0.5, Y-H*0.5) (浮点数乘法更安全)。
避免写法：X-W/2 (整数除法可能导致精度丢失或边缘伪影)。
                    【✅ 最终输出示例】
                    -filter_complex "split[v1][v2];[v2]hue=s=0[bw];[v1][bw]hstack" -c:v libx264 -preset ultrafast -c:a copy
                    -vf "split[m][p];[p]scale=iw/3:-1,rotate=n/10:c=none:ow=rotw(iw):oh=roth(ih)[pr];[m][pr]overlay=x='W/2+W/3*cos(n/20)-w/2':y='H/2+H/4*sin(n/20)-h/2',format=yuv420p" -c:v libx264 -preset ultrafast -c:a copy "output"
                    -filter_complex "split[main][to_neg];[to_neg]negate,split[neg1][neg2];[neg2]scale=320:-1,format=yuv420p,geq=lum='if(gt(1200/(hypot(X-W*(0.5+0.2*sin(T)),Y-H*(0.5+0.2*cos(T*1.1)))+5)+1200/(hypot(X-W*(0.3+0.2*cos(T*0.8)),Y-H*(0.3+0.1*sin(T*1.3)))+5)+1200/(hypot(X-W*(0.7+0.1*sin(T*1.5)),Y-H*(0.7+0.2*cos(T*0.9)))+5)+1100/(hypot(X-W*(0.2+0.1*cos(T*2)),Y-H*(0.8+0.1*sin(T*1.7)))+5)+1100/(hypot(X-W*(0.8+0.1*sin(T*1.2)),Y-H*(0.2+0.1*cos(T*2.2)))+5),45+15*sin(T*2)),255,0)':cb=128:cr=128,scale=1080:1920:flags=neighbor[mask];[neg1][mask]alphamerge[ball];[main][ball]overlay=format=auto,format=yuv420p" -c:v libx264 -preset ultrafast -c:a copy
                
                """.trimIndent()

                val wrapper = JSONObject()
                wrapper.put("0_instruction_for_ai", instruction)
                wrapper.put("media_data", JSONObject(rawJson))
                wrapper.toString(2)
            } else {
                "获取信息失败: RC=${session.returnCode}"
            }
        } catch (e: Exception) {
            "异常: ${e.message}"
        }
    }

    private fun parseDuration(log: String): Long {
        return try {
            val pattern = """Duration:\s+(\d+):(\d+):(\d+(?:\.\d+)?)""".toRegex()
            val match = pattern.find(log) ?: return 0L
            val (h, m, s) = match.destructured
            ((h.toLong() * 3600 + m.toLong() * 60 + s.toDouble()) * 1000).toLong()
        } catch (e: Exception) {
            0L
        }
    }
}