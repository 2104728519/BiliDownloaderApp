package com.example.bilidownloader.utils

import io.microshow.rxffmpeg.RxFFmpegInvoke
import io.microshow.rxffmpeg.RxFFmpegSubscriber
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * 剪辑师助手
 * 负责调用 RxFFmpeg 库进行音视频合并和转码
 */
object FFmpegHelper {

    /**
     * 合并视频和音频 (生成 MP4)
     */
    suspend fun mergeVideoAudio(videoFile: File, audioFile: File, outFile: File): Boolean {
        val commands = arrayOf(
            "ffmpeg",
            "-y",
            "-i", videoFile.absolutePath,
            "-i", audioFile.absolutePath,
            "-c", "copy",
            outFile.absolutePath
        )

        println("FFmpeg命令: ${commands.joinToString(" ")}")

        return runCommand(commands)
    }

    /**
     * 【新功能】把音频流转码成 MP3
     */
    suspend fun convertAudioToMp3(audioFile: File, outFile: File): Boolean {
        val commands = arrayOf(
            "ffmpeg",
            "-y",
            "-i", audioFile.absolutePath,
            "-vn", // 不要视频
            "-acodec", "libmp3lame", // MP3 编码器
            "-q:a", "2", // 高音质
            outFile.absolutePath
        )

        println("FFmpeg音频转换命令: ${commands.joinToString(" ")}")

        return runCommand(commands)
    }

    /**
     * 【新增】裁剪音频
     * @param inputFile: 源文件
     * @param outFile: 输出文件
     * @param startTime: 开始时间 (秒)
     * @param duration: 持续时长 (秒)
     */
    suspend fun trimAudio(inputFile: File, outFile: File, startTime: Double, duration: Double): Boolean {
        val commands = arrayOf(
            "ffmpeg",
            "-y",
            "-i", inputFile.absolutePath,
            "-ss", String.format("%.3f", startTime), // -ss: 寻找到开始时间
            "-t", String.format("%.3f", duration),   // -t: 持续时间
            "-acodec", "libmp3lame", // 统一转成 MP3
            "-q:a", "2", // 高音质 VBR 模式，质量为 2 (0-9，越低越好)
            outFile.absolutePath
        )

        println("FFmpeg裁剪命令: ${commands.joinToString(" ")}")

        // 由于裁剪逻辑与合并/转换逻辑一致，我们可以复用 runCommand 函数
        return runCommand(commands)
    }

    // 提取公共逻辑：执行 FFmpeg 命令
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