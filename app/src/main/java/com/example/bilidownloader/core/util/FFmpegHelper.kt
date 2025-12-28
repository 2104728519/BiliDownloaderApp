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
     * 采用 `-c:v copy` 快速流复制视频，音频统一转码为 320k AAC 以保证兼容性。
     * @param videoFile 纯视频轨道文件.
     * @param audioFile 纯音频轨道文件.
     * @param outFile 输出文件路径.
     */
    suspend fun mergeVideoAudio(videoFile: File, audioFile: File, outFile: File): Boolean {
        // FFmpegKit 命令不需要 "ffmpeg" 前缀，且路径建议加引号防止空格问题
        val command = "-y " +
                "-i \"${videoFile.absolutePath}\" " +
                "-i \"${audioFile.absolutePath}\" " +
                "-c:v copy " +
                "-c:a aac " +
                "-b:a 320k " +
                "-strict experimental " +
                "\"${outFile.absolutePath}\""

        return runCommand(command)
    }

    /**
     * 封装为 FLAC 无损音频.
     * 直接流复制，不进行重编码。
     */
    suspend fun remuxToFlac(inputFile: File, outFile: File): Boolean {
        val command = "-y " +
                "-i \"${inputFile.absolutePath}\" " +
                "-c copy " +
                "\"${outFile.absolutePath}\""

        return runCommand(command)
    }

    /**
     * 音频转码为 MP3 (VBR 高质量).
     */
    suspend fun convertAudioToMp3(audioFile: File, outFile: File): Boolean {
        val command = "-y " +
                "-i \"${audioFile.absolutePath}\" " +
                "-vn " + // 禁用视频流
                "-acodec libmp3lame " +
                "-q:a 2 " + // VBR 质量等级 2
                "\"${outFile.absolutePath}\""

        return runCommand(command)
    }

    /**
     * 精确裁剪音频.
     *
     * @param startTime 开始时间 (秒).
     * @param duration 持续时长 (秒).
     * 注意：使用 libmp3lame 重编码以确保裁剪点准确，避免关键帧问题。
     */
    suspend fun trimAudio(inputFile: File, outFile: File, startTime: Double, duration: Double): Boolean {
        // 格式化时间，保留3位小数
        val ss = String.format("%.3f", startTime)
        val t = String.format("%.3f", duration)

        val command = "-y " +
                "-i \"${inputFile.absolutePath}\" " +
                "-ss $ss " + // 精确到毫秒级定位
                "-t $t " +
                "-acodec libmp3lame " +
                "-q:a 2 " +
                "\"${outFile.absolutePath}\""

        Log.d(TAG, "FFmpeg裁剪命令: $command")
        return runCommand(command)
    }

    /**
     * 内部方法：执行 FFmpeg 命令并监听结果.
     * 使用 FFmpegKit.executeAsync 异步执行，并在协程中挂起等待。
     */
    private suspend fun runCommand(command: String): Boolean {
        return suspendCancellableCoroutine { continuation ->

            Log.d(TAG, "Executing FFmpeg command: $command")

            // 使用 FFmpegKit 异步执行
            val session = FFmpegKit.executeAsync(command) { session ->
                val returnCode = session.returnCode

                // 处理完成回调
                if (ReturnCode.isSuccess(returnCode)) {
                    Log.d(TAG, "FFmpeg Success")
                    if (continuation.isActive) continuation.resume(true)
                } else {
                    Log.e(TAG, "FFmpeg Failed with state: ${session.state}, rc: $returnCode")
                    // 获取错误日志
                    val failStackTrace = session.failStackTrace
                    Log.e(TAG, "Error Log: $failStackTrace")

                    if (continuation.isActive) continuation.resume(false)
                }
            }

            // 处理协程取消事件 (如用户点击了取消下载)
            continuation.invokeOnCancellation {
                Log.w(TAG, "Canceling FFmpeg session: ${session.sessionId}")
                FFmpegKit.cancel(session.sessionId)
            }
        }
    }
}