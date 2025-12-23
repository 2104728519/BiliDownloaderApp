package com.example.bilidownloader.features.home

import android.content.Context
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.core.util.BiliSigner
import com.example.bilidownloader.core.util.FFmpegHelper
import com.example.bilidownloader.core.util.StorageHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.util.TreeMap

/**
 * 下载核心仓库.
 *
 * 职责：
 * 1. 底层文件下载 (支持断点续传、重试).
 * 2. 视频下载编排 (获取链接 -> 下载流 -> FFmpeg 合并 -> 存入相册).
 * 3. 音频提取编排 (用于 AI 转写).
 */
class DownloadRepository(private val context: Context) {

    private val client = NetworkModule.downloadClient
    private val apiService = NetworkModule.biliService

    // region 1. High-Level Orchestration (业务编排)

    data class DownloadParams(
        val bvid: String,
        val cid: Long,
        val videoId: Int?,     // 视频流 ID (qn)
        val videoCodecs: String?,
        val audioId: Int,      // 音频流 ID
        val audioCodecs: String?,
        val audioOnly: Boolean
    )

    /**
     * 执行完整下载流程 (获取链接 -> 下载流 -> 合并 -> 保存).
     * 原 DownloadVideoUseCase 逻辑.
     */
    fun downloadVideo(params: DownloadParams): Flow<Resource<String>> = flow {
        emit(Resource.Loading(progress = 0f, data = "准备下载..."))
        var videoFile: File? = null
        var audioFile: File? = null
        var outMp4: File? = null

        try {
            val cacheDir = context.cacheDir
            // 1. 刷新链接 (防过期)
            // 优先使用视频画质ID，如果是纯音频则使用音频ID
            val qn = params.videoId ?: params.audioId
            val queryMap = getSignedParams(params.bvid, params.cid, qn)

            val playResp = apiService.getPlayUrl(queryMap).execute()
            val body = playResp.body()

            if (body?.code != 0) {
                throw Exception("API 错误: ${body?.message}")
            }

            val dash = body.data?.dash ?: throw Exception("不支持的格式(非DASH)")

            // 2. 匹配音频 URL
            // 优先匹配 FLAC，其次匹配选中的 ID，最后兜底第一个
            val audioUrl = if (params.audioCodecs == "flac") {
                dash.flac?.audio?.baseUrl
            } else {
                null
            } ?: dash.audio?.find { it.id == params.audioId }?.baseUrl
            ?: dash.audio?.firstOrNull()?.baseUrl
            ?: throw Exception("未找到音频流")

            // 准备音频临时文件
            val audioSuffix = if (params.audioCodecs == "flac") ".flac" else ".mp3"
            // 使用简单的临时文件名，避免特殊字符问题
            audioFile = File(cacheDir, "${params.bvid}_${params.cid}_audio.tmp")

            // ================================================================
            // 分支 A: 仅下载音频 (Audio Only Mode)
            // ================================================================
            if (params.audioOnly) {
                val outAudio = File(cacheDir, "${params.bvid}_out$audioSuffix")

                // 下载音频
                downloadFile(audioUrl, audioFile).collect { p ->
                    emit(Resource.Loading(p * 0.8f, "下载音频中..."))
                }

                emit(Resource.Loading(0.9f, "正在转码/封装..."))

                // 格式处理
                val success = if (params.audioCodecs == "flac") {
                    FFmpegHelper.remuxToFlac(audioFile, outAudio)
                } else {
                    FFmpegHelper.convertAudioToMp3(audioFile, outAudio)
                }

                if (!success) throw Exception("音频处理失败")

                // 保存到音乐库
                StorageHelper.saveAudioToMusic(context, outAudio, "Bili_${params.bvid}$audioSuffix")

                // 清理
                outAudio.delete()
                audioFile.delete()

                emit(Resource.Success("音频已保存到音乐库"))
            }
            // ================================================================
            // 分支 B: 完整视频下载 (Merge Video + Audio)
            // ================================================================
            else {
                // 匹配视频 URL
                val videoUrl = dash.video.find { it.id == params.videoId && it.codecs == params.videoCodecs }?.baseUrl
                    ?: dash.video.find { it.id == params.videoId }?.baseUrl
                    ?: dash.video.firstOrNull()?.baseUrl
                    ?: throw Exception("未找到视频流")

                videoFile = File(cacheDir, "${params.bvid}_${params.cid}_video.tmp")
                outMp4 = File(cacheDir, "${params.bvid}_final.mp4")

                // 1. 下载视频流 (权重 45%)
                downloadFile(videoUrl, videoFile).collect { p ->
                    emit(Resource.Loading(p * 0.45f, "下载视频流..."))
                }

                // 2. 下载音频流 (权重 45%)
                downloadFile(audioUrl, audioFile).collect { p ->
                    emit(Resource.Loading(0.45f + p * 0.45f, "下载音频流..."))
                }

                // 3. FFmpeg 混流
                emit(Resource.Loading(0.95f, "正在合并音视频..."))
                if (!FFmpegHelper.mergeVideoAudio(videoFile, audioFile, outMp4)) {
                    throw Exception("FFmpeg 合并失败")
                }

                // 4. 保存到相册
                emit(Resource.Loading(0.99f, "正在保存..."))
                StorageHelper.saveVideoToGallery(context, outMp4, "Bili_${params.bvid}.mp4")

                // 清理
                outMp4.delete()
                emit(Resource.Success("视频已保存到相册"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emit(Resource.Error("下载失败: ${e.message}"))
        } finally {
            // 确保清理临时文件
            try {
                videoFile?.delete()
                audioFile?.delete()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 提取音频用于转写 (原 PrepareTranscribeUseCase).
     * 下载低画质流中的音频轨道到缓存目录。
     */
    fun downloadAudioToCache(bvid: String, cid: Long): Flow<Resource<String>> = flow {
        emit(Resource.Loading(0f, "准备音频..."))
        try {
            // 获取低画质链接 (qn=80)
            val queryMap = getSignedParams(bvid, cid, 80)
            val playResp = apiService.getPlayUrl(queryMap).execute()
            val data = playResp.body()?.data

            // 优先 DASH 音频，其次 durl
            val audioUrl = data?.dash?.audio?.firstOrNull()?.baseUrl
                ?: data?.durl?.firstOrNull()?.url
                ?: throw Exception("未找到音频流")

            val tempFile = File(context.cacheDir, "trans_${System.currentTimeMillis()}.m4a")

            downloadFile(audioUrl, tempFile).collect { p ->
                emit(Resource.Loading(p, "提取音频中... ${(p * 100).toInt()}%"))
            }

            emit(Resource.Success(tempFile.absolutePath))

        } catch (e: Exception) {
            e.printStackTrace()
            emit(Resource.Error(e.message ?: "提取失败"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 辅助方法：获取带 WBI 签名的参数 Map
     */
    private fun getSignedParams(bvid: String, cid: Long, quality: Int): Map<String, String> {
        val navResp = apiService.getNavInfo().execute()
        val navData = navResp.body()?.data ?: throw Exception("密钥获取失败")

        val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
        val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
        val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

        val params = TreeMap<String, Any>().apply {
            put("bvid", bvid)
            put("cid", cid)
            put("qn", quality)
            put("fnval", "4048")
            put("fourk", "1")
        }

        val signed = BiliSigner.signParams(params, mixinKey)

        // Retrofit @QueryMap 需要解码后的参数
        return signed.split("&").associate {
            val p = it.split("=")
            URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8")
        }
    }

    // endregion

    // region 2. Low-Level Downloader (底层下载器)

    /**
     * 基础文件下载方法.
     * 支持断点续传 (Range Header) 和自动重试.
     */
    fun downloadFile(url: String, file: File): Flow<Float> = flow {
        var currentLength = if (file.exists()) file.length() else 0L
        var totalLength = 0L
        var retryCount = 0
        val maxRetries = 10

        while (true) {
            var response: Response? = null

            try {
                // 1. 构建请求
                val requestBuilder = Request.Builder()
                    .url(url)
                    .addHeader("Referer", "https://www.bilibili.com/")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

                // 2. 设置断点 (Range: bytes=xxx-)
                if (currentLength > 0) {
                    requestBuilder.addHeader("Range", "bytes=$currentLength-")
                }

                // 3. 执行请求
                response = client.newCall(requestBuilder.build()).execute()
                val body = response.body

                // 416 Range Not Satisfiable: 说明文件已下载完成
                if (response.code == 416) {
                    emit(1.0f)
                    return@flow
                }

                if (!response.isSuccessful || body == null) {
                    throw Exception("HTTP Error: ${response.code}")
                }

                val contentLength = body.contentLength()
                if (contentLength == 0L) {
                    emit(1.0f)
                    return@flow
                }

                // 206 Partial Content: 续传成功
                if (response.code == 206) {
                    totalLength = currentLength + contentLength
                } else {
                    // 200 OK: 服务器不支持续传或文件被重置
                    currentLength = 0
                    totalLength = contentLength
                    if (file.exists()) file.delete()
                    file.createNewFile()
                }

                val inputStream: InputStream = body.byteStream()

                // 使用 RandomAccessFile 实现从指定位置写入
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(currentLength)

                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var bytesSinceEmit = 0L

                    retryCount = 0 // 连接成功，重置重试计数器

                    while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
                        // 响应协程取消信号
                        currentCoroutineContext().ensureActive()

                        raf.write(buffer, 0, bytesRead)
                        currentLength += bytesRead
                        bytesSinceEmit += bytesRead

                        // 减少 emit 频率，避免 UI 刷新过快 (每 100KB 刷新一次)
                        if (bytesSinceEmit > 100 * 1024) {
                            val progress = if (totalLength > 0) currentLength.toFloat() / totalLength.toFloat() else 0f
                            emit(progress)
                            bytesSinceEmit = 0
                        }
                    }
                }

                emit(1.0f)
                return@flow

            } catch (e: Exception) {
                // 若为用户主动取消，则不重试
                if (e is CancellationException) {
                    throw e
                }

                if (retryCount >= maxRetries) {
                    throw Exception("超过最大重试次数: ${e.message}")
                }
                retryCount++

                val waitTime = 1000L * retryCount
                // 发送旧进度以维持 UI 状态
                val progress = if (totalLength > 0) currentLength.toFloat() / totalLength.toFloat() else 0f
                emit(progress)
                delay(waitTime)
            } finally {
                // 确保释放网络连接
                try {
                    response?.close()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    // endregion
}