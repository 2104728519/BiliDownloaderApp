package com.example.bilidownloader.core.util

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * FFmpeg 命令执行封装工具.
 *
 * 负责构建并执行具体的 FFmpeg 指令，用于音视频合并、格式转换和裁剪。
 * 已从 RxFFmpeg 迁移至 FFmpegKit。
 */
object FFmpegHelper {

    private const val TAG = "FFmpegHelper"

    /**
     * 合并独立的视频流和音频流.
     *
     * 优化：采用 `-c copy` 直接流复制，不进行重编码，速度提升巨大。
     * @param videoFile 纯视频轨道文件.
     * @param audioFile 纯音频轨道文件.
     * @param outFile 输出文件路径.
     * @param durationMs 视频总时长 (毫秒)，用于计算进度.
     * @param onProgress 进度回调 (0.0 ~ 1.0).
     */
    suspend fun mergeVideoAudio(
        videoFile: File,
        audioFile: File,
        outFile: File,
        durationMs: Long = 0,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        // 使用 -c copy 极速合并，B站DASH流音视频格式通常已兼容 MP4 容器
        val command = "-y " +
                "-i \"${videoFile.absolutePath}\" " +
                "-i \"${audioFile.absolutePath}\" " +
                "-c copy " +
                "-map 0:v:0 " +
                "-map 1:a:0 " +
                "-strict experimental " +
                "\"${outFile.absolutePath}\""

        return runCommand(command, durationMs, onProgress)
    }

    /**
     * 封装为 FLAC 无损音频.
     * 直接流复制，不进行重编码。
     */
    suspend fun remuxToFlac(
        inputFile: File,
        outFile: File,
        durationMs: Long = 0,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        val command = "-y " +
                "-i \"${inputFile.absolutePath}\" " +
                "-c copy " +
                "\"${outFile.absolutePath}\""

        return runCommand(command, durationMs, onProgress)
    }

    /**
     * 音频转码为 MP3 (VBR 高质量).
     */
    suspend fun convertAudioToMp3(
        audioFile: File,
        outFile: File,
        durationMs: Long = 0,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        val command = "-y " +
                "-i \"${audioFile.absolutePath}\" " +
                "-vn " + // 禁用视频流
                "-acodec libmp3lame " +
                "-q:a 2 " + // VBR 质量等级 2
                "\"${outFile.absolutePath}\""

        return runCommand(command, durationMs, onProgress)
    }

    /**
     * 通用快速裁剪 (不重编码).
     *
     * @param inputFile 输入文件.
     * @param outFile 输出文件.
     * @param startSeconds 开始时间 (秒).
     * @param durationSeconds 持续时长 (秒).
     * @param onProgress 进度回调.
     */
    suspend fun fastCrop(
        inputFile: File,
        outFile: File,
        startSeconds: Float,
        durationSeconds: Float,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        val ss = String.format("%.3f", startSeconds)
        val t = String.format("%.3f", durationSeconds)

        // 快速裁剪：-ss 放在 -i 之前。使用 -c copy 保持原编码。
        val command =
            "-y -ss $ss -i \"${inputFile.absolutePath}\" -t $t -c copy \"${outFile.absolutePath}\""

        Log.d(TAG, "FFmpeg快速裁剪命令: $command")
        return runCommand(command, (durationSeconds * 1000).toLong(), onProgress)
    }

    /**
     * 裁剪音频.
     *
     * @param startTime 开始时间 (秒).
     * @param duration 持续时长 (秒).
     * @param precise 是否精确裁剪.
     *        精确模式：重编码以确保切点准确。
     *        快速模式：使用 -c copy 极速流拷贝，但切点可能偏移。
     */
    suspend fun trimAudio(
        inputFile: File,
        outFile: File,
        startTime: Double,
        duration: Double,
        precise: Boolean = true
    ): Boolean {
        val ss = String.format("%.3f", startTime)
        val t = String.format("%.3f", duration)

        val command = if (precise) {
            // 精确裁剪：-i 在 -ss 之前，并进行重编码 (不加 -c copy 即重编码)
            "-y -i \"${inputFile.absolutePath}\" -ss $ss -t $t \"${outFile.absolutePath}\""
        } else {
            // 快速裁剪：-ss 在 -i 之前实现快进，使用 -c copy 极速流拷贝
            "-y -ss $ss -i \"${inputFile.absolutePath}\" -t $t -c copy \"${outFile.absolutePath}\""
        }

        Log.d(TAG, "FFmpeg裁剪命令: $command")
        return runCommand(command)
    }

    /**
     * 内部方法：执行 FFmpeg 命令并监听结果.
     * 使用 FFmpegKit.executeAsync 异步执行，并在协程中挂起等待。
     */
    private suspend fun runCommand(
        command: String,
        durationMs: Long = 0,
        onProgress: ((Float) -> Unit)? = null
    ): Boolean {
        return suspendCancellableCoroutine { continuation ->

            Log.d(TAG, "Executing FFmpeg command: $command")

            // 使用 FFmpegKit 异步执行
            val session = FFmpegKit.executeAsync(command, { session ->
                val returnCode = session.returnCode

                // 处理完成回调
                if (ReturnCode.isSuccess(returnCode)) {
                    Log.d(TAG, "FFmpeg Success")
                    onProgress?.invoke(1f)
                    if (continuation.isActive) continuation.resume(true)
                } else {
                    Log.e(TAG, "FFmpeg Failed with state: ${session.state}, rc: $returnCode")
                    // 获取错误日志
                    val failStackTrace = session.failStackTrace
                    Log.e(TAG, "Error Log: $failStackTrace")

                    if (continuation.isActive) continuation.resume(false)
                }
            }, { /* Log callback */ }, { statistics ->
                // 统计回调，用于计算进度
                if (onProgress != null && durationMs > 0) {
                    val timeInMs = statistics.time
                    val progress = (timeInMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
                    onProgress.invoke(progress)
                }
            })

            // 处理协程取消事件 (如用户点击了取消下载)
            continuation.invokeOnCancellation {
                Log.w(TAG, "Canceling FFmpeg session: ${session.sessionId}")
                FFmpegKit.cancel(session.sessionId)
            }
        }
    }
}
