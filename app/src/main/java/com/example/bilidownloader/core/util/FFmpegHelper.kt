package com.example.bilidownloader.core.util

import io.microshow.rxffmpeg.RxFFmpegInvoke
import io.microshow.rxffmpeg.RxFFmpegSubscriber
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * FFmpeg 命令执行封装工具.
 *
 * 负责构建并执行具体的 FFmpeg 指令，用于音视频合并、格式转换和裁剪。
 * 使用 suspendCancellableCoroutine 将基于回调的 RxFFmpeg 库转换为协程挂起函数。
 */
object FFmpegHelper {

    /**
     * 合并独立的视频流和音频流.
     *
     * 采用 `-c:v copy` 快速流复制视频，音频统一转码为 320k AAC 以保证兼容性。
     * @param videoFile 纯视频轨道文件.
     * @param audioFile 纯音频轨道文件.
     * @param outFile 输出文件路径.
     */
    suspend fun mergeVideoAudio(videoFile: File, audioFile: File, outFile: File): Boolean {
        val commands = arrayOf(
            "ffmpeg",
            "-y",
            "-i", videoFile.absolutePath,
            "-i", audioFile.absolutePath,
            "-c:v", "copy",
            "-c:a", "aac",
            "-b:a", "320k",
            "-strict", "experimental",
            outFile.absolutePath
        )
        return runCommand(commands)
    }

    /**
     * 封装为 FLAC 无损音频.
     * 直接流复制，不进行重编码。
     */
    suspend fun remuxToFlac(inputFile: File, outFile: File): Boolean {
        val commands = arrayOf(
            "ffmpeg",
            "-y",
            "-i", inputFile.absolutePath,
            "-c", "copy",
            outFile.absolutePath
        )
        return runCommand(commands)
    }

    /**
     * 音频转码为 MP3 (VBR 高质量).
     */
    suspend fun convertAudioToMp3(audioFile: File, outFile: File): Boolean {
        val commands = arrayOf(
            "ffmpeg",
            "-y",
            "-i", audioFile.absolutePath,
            "-vn", // 禁用视频流
            "-acodec", "libmp3lame",
            "-q:a", "2", // VBR 质量等级 2 (约为 170-210kbps)
            outFile.absolutePath
        )
        return runCommand(commands)
    }

    /**
     * 精确裁剪音频.
     *
     * @param startTime 开始时间 (秒).
     * @param duration 持续时长 (秒).
     * 注意：使用 libmp3lame 重编码以确保裁剪点准确，避免关键帧问题。
     */
    suspend fun trimAudio(inputFile: File, outFile: File, startTime: Double, duration: Double): Boolean {
        val commands = arrayOf(
            "ffmpeg",
            "-y",
            "-i", inputFile.absolutePath,
            "-ss", String.format("%.3f", startTime), // 精确到毫秒级定位
            "-t", String.format("%.3f", duration),
            "-acodec", "libmp3lame",
            "-q:a", "2",
            outFile.absolutePath
        )
        println("FFmpeg裁剪命令: ${commands.joinToString(" ")}")
        return runCommand(commands)
    }

    /**
     * 内部方法：执行 FFmpeg 命令并监听结果.
     */
    private suspend fun runCommand(commands: Array<String>): Boolean {
        return suspendCancellableCoroutine { continuation ->
            val mySubscriber = object : RxFFmpegSubscriber() {
                override fun onFinish() {
                    if (continuation.isActive) continuation.resume(true)
                }
                override fun onError(message: String?) {
                    println("FFmpeg出错: $message")
                    if (continuation.isActive) continuation.resume(false)
                }
                override fun onProgress(progress: Int, progressTime: Long) {}
                override fun onCancel() {
                    if (continuation.isActive) continuation.resume(false)
                }
            }
            RxFFmpegInvoke.getInstance().runCommandRxJava(commands).subscribe(mySubscriber)
            continuation.invokeOnCancellation { RxFFmpegInvoke.getInstance().exit() }
        }
    }
}