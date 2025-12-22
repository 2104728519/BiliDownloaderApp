package com.example.bilidownloader.core.util

import io.microshow.rxffmpeg.RxFFmpegInvoke
import io.microshow.rxffmpeg.RxFFmpegSubscriber
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * 剪辑师助手
 * 负责调用 RxFFmpeg 库进行音视频合并、转码和剪辑
 */
object FFmpegHelper {

    /**
     * 合并视频和音频
     * 为了兼容性，音频转码为 320k AAC
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
     * 封装 FLAC 音频 (无损)
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
     * 音频转码为 MP3
     */
    suspend fun convertAudioToMp3(audioFile: File, outFile: File): Boolean {
        val commands = arrayOf(
            "ffmpeg",
            "-y",
            "-i", audioFile.absolutePath,
            "-vn",
            "-acodec", "libmp3lame",
            "-q:a", "2",
            outFile.absolutePath
        )
        return runCommand(commands)
    }

    /**
     * 【新增】裁剪音频 (修复报错的核心方法)
     * @param inputFile 源文件
     * @param outFile 输出文件
     * @param startTime 开始时间 (秒)
     * @param duration 持续时长 (秒)
     */
    suspend fun trimAudio(inputFile: File, outFile: File, startTime: Double, duration: Double): Boolean {
        val commands = arrayOf(
            "ffmpeg",
            "-y",
            "-i", inputFile.absolutePath,
            "-ss", String.format("%.3f", startTime), // 精确到毫秒
            "-t", String.format("%.3f", duration),
            "-acodec", "libmp3lame", // 统一剪辑为 MP3 格式
            "-q:a", "2", // 高音质 VBR
            outFile.absolutePath
        )
        println("FFmpeg裁剪命令: ${commands.joinToString(" ")}")
        return runCommand(commands)
    }

    // 内部执行命令的方法
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