package com.example.bilidownloader.features.home

import android.content.Context
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.core.util.BiliSigner
import com.example.bilidownloader.core.util.FFmpegHelper
import com.example.bilidownloader.core.util.StorageHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.URLDecoder
import java.util.TreeMap

/**
 * 下载核心仓库.
 */
class DownloadRepository(private val context: Context) {

    private val client = NetworkModule.downloadClient
    private val apiService = NetworkModule.biliService

    data class DownloadParams(
        val bvid: String,
        val cid: Long,
        val videoId: Int?,     // 视频流 ID (qn)
        val videoCodecs: String?,
        val audioId: Int,      // 音频流 ID
        val audioCodecs: String?,
        val audioOnly: Boolean,
        val pageTitle: String? = null // 分P标题
    )

    /**
     * 执行完整下载流程 (获取链接 -> 下载流 -> 合并 -> 保存).
     */
    fun downloadVideo(params: DownloadParams): Flow<Resource<String>> = flow {
        emit(Resource.Loading(progress = 0f, data = "准备下载..."))
        var videoFile: File? = null
        var audioFile: File? = null
        var outMp4: File? = null

        try {
            val cacheDir = context.cacheDir
            val qn = params.videoId ?: params.audioId
            val queryMap = getSignedParams(params.bvid, params.cid, qn)

            val playResp = apiService.getPlayUrl(queryMap).execute()
            val body = playResp.body()

            if (body?.code != 0) {
                throw Exception("API 错误: ${body?.message}")
            }

            val dash = body.data?.dash ?: throw Exception("不支持的格式(非DASH)")
            val durationMs = body.data?.timelength ?: 0L

            // 1. 匹配音频 URL
            val audioUrl = if (params.audioCodecs == "flac") {
                dash.flac?.audio?.baseUrl
            } else {
                null
            } ?: dash.audio?.find { it.id == params.audioId }?.baseUrl
            ?: dash.audio?.firstOrNull()?.baseUrl
            ?: throw Exception("未找到音频流")

            val audioSuffix = if (params.audioCodecs == "flac") ".flac" else ".mp3"
            audioFile = File(cacheDir, "${params.bvid}_${params.cid}_audio.tmp")

            val titleSuffix = if (params.pageTitle.isNullOrEmpty()) "" else "_${params.pageTitle}"
            val safeTitleSuffix = titleSuffix.replace("[\\\\/:*?\"<>|]".toRegex(), "_")

            if (params.audioOnly) {
                val outAudio = File(cacheDir, "${params.bvid}_${params.cid}_out$audioSuffix")

                downloadFile(audioUrl, audioFile).collect { (current, total) ->
                    val progress = if (total > 0) current.toFloat() / total.toFloat() else 0f
                    val detail = "${formatSize(current)} / ${formatSize(total)}"
                    emit(Resource.Loading(progress * 0.9f, "下载音频中...|DETAIL:$detail"))
                }

                emit(Resource.Loading(0.95f, "正在转码/封装..."))
                val success = if (params.audioCodecs == "flac") {
                    FFmpegHelper.remuxToFlac(audioFile, outAudio)
                } else {
                    FFmpegHelper.convertAudioToMp3(audioFile, outAudio)
                }
                if (!success) throw Exception("音频处理失败")

                StorageHelper.saveAudioToMusic(
                    context,
                    outAudio,
                    "Bili_${params.bvid}${safeTitleSuffix}$audioSuffix"
                )
                outAudio.delete()
                audioFile.delete()
                emit(Resource.Success("音频已保存到音乐库"))
            } else {
                val videoUrl = dash.video.find { it.id == params.videoId && it.codecs == params.videoCodecs }?.baseUrl
                    ?: dash.video.find { it.id == params.videoId }?.baseUrl
                    ?: dash.video.firstOrNull()?.baseUrl
                    ?: throw Exception("未找到视频流")

                videoFile = File(cacheDir, "${params.bvid}_${params.cid}_video.tmp")
                outMp4 = File(cacheDir, "${params.bvid}_${params.cid}_final.mp4")

                var videoTotal = 0L
                var audioTotal = 0L
                var videoCurrent = 0L
                var audioCurrent = 0L

                // 1. 下载视频
                downloadFile(videoUrl, videoFile).collect { (current, total) ->
                    videoCurrent = current
                    videoTotal = total
                    val overallTotal =
                        if (audioTotal > 0) videoTotal + audioTotal else (videoTotal * 1.1).toLong()
                    val progress = videoCurrent.toFloat() / overallTotal.toFloat()
                    val detail = "${formatSize(videoCurrent)} / ${formatSize(videoTotal)}"
                    emit(Resource.Loading(progress, "下载视频流...|DETAIL:$detail"))
                }

                // 2. 下载音频
                downloadFile(audioUrl, audioFile).collect { (current, total) ->
                    audioCurrent = current
                    audioTotal = total
                    val overallTotal = videoTotal + audioTotal
                    val progress = (videoTotal + audioCurrent).toFloat() / overallTotal.toFloat()
                    val detail = "${formatSize(audioCurrent)} / ${formatSize(audioTotal)}"
                    emit(Resource.Loading(progress, "下载音频流...|DETAIL:$detail"))
                }

                // 3. 合并阶段
                val channel = Channel<Float>(Channel.CONFLATED)
                var success = false

                coroutineScope {
                    val ffmpegJob = launch(Dispatchers.IO) {
                        success = FFmpegHelper.mergeVideoAudio(
                            videoFile!!,
                            audioFile!!,
                            outMp4!!,
                            durationMs
                        ) { p ->
                            channel.trySend(p)
                        }
                        channel.close()
                    }

                    for (p in channel) {
                        emit(Resource.Loading(1.0f, "正在合并音视频...|MERGE:$p"))
                    }
                    ffmpegJob.join()
                }

                if (!success) throw Exception("FFmpeg 合并失败")

                emit(Resource.Loading(1.0f, "正在保存...|MERGE:1.0"))
                StorageHelper.saveVideoToGallery(
                    context,
                    outMp4!!,
                    "Bili_${params.bvid}${safeTitleSuffix}.mp4"
                )
                outMp4?.delete()
                emit(Resource.Success("视频已保存到相册"))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emit(Resource.Error(e.message ?: "下载失败"))
        } finally {
            try {
                videoFile?.delete()
                audioFile?.delete()
            } catch (e: Exception) { e.printStackTrace() }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 提取音频用于转写.
     */
    fun downloadAudioToCache(bvid: String, cid: Long): Flow<Resource<String>> = flow {
        emit(Resource.Loading(0f, "准备音频..."))
        try {
            val queryMap = getSignedParams(bvid, cid, 80)
            val playResp = apiService.getPlayUrl(queryMap).execute()
            val data = playResp.body()?.data

            val audioUrl = data?.dash?.audio?.firstOrNull()?.baseUrl
                ?: data?.durl?.firstOrNull()?.url
                ?: throw Exception("未找到音频流")

            val tempFile = File(context.cacheDir, "trans_${System.currentTimeMillis()}.m4a")

            downloadFile(audioUrl, tempFile).collect { (current, total) ->
                val p = if (total > 0) current.toFloat() / total.toFloat() else 0f
                emit(Resource.Loading(p, "提取音频中... ${(p * 100).toInt()}%"))
            }

            emit(Resource.Success(tempFile.absolutePath))
        } catch (e: Exception) {
            e.printStackTrace()
            emit(Resource.Error(e.message ?: "提取失败"))
        }
    }.flowOn(Dispatchers.IO)

    private fun formatSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(
            "%.2f %s",
            bytes / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups]
        )
    }

    /**
     * 基础文件下载方法. 返回 (当前字节, 总字节)
     */
    fun downloadFile(url: String, file: File): Flow<Pair<Long, Long>> = flow {
        var currentLength = if (file.exists()) file.length() else 0L
        var totalLength = 0L
        var retryCount = 0
        val maxRetries = 10

        while (true) {
            var response: Response? = null
            try {
                val requestBuilder = Request.Builder()
                    .url(url)
                    .addHeader("Referer", "https://www.bilibili.com/")
                    .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")

                if (currentLength > 0) {
                    requestBuilder.addHeader("Range", "bytes=$currentLength-")
                }

                response = client.newCall(requestBuilder.build()).execute()
                val body = response.body

                if (response.code == 416) {
                    emit(currentLength to currentLength)
                    return@flow
                }

                if (!response.isSuccessful || body == null) throw Exception("HTTP ${response.code}")

                val contentLength = body.contentLength()
                totalLength =
                    if (response.code == 206) currentLength + contentLength else contentLength

                if (response.code != 206) {
                    currentLength = 0
                    if (file.exists()) file.delete()
                    file.createNewFile()
                }

                val inputStream: InputStream = body.byteStream()
                RandomAccessFile(file, "rw").use { raf ->
                    raf.seek(currentLength)
                    val buffer = ByteArray(32 * 1024)
                    var bytesRead: Int
                    var lastReported = 0L

                    while (inputStream.read(buffer).also { bytesRead = it } >= 0) {
                        currentCoroutineContext().ensureActive()
                        raf.write(buffer, 0, bytesRead)
                        currentLength += bytesRead

                        if (currentLength - lastReported > 512 * 1024 || currentLength == totalLength) {
                            emit(currentLength to totalLength)
                            lastReported = currentLength
                        }
                    }
                }
                return@flow
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (retryCount >= maxRetries) throw e
                retryCount++
                delay(1000L * retryCount)
            } finally {
                response?.close()
            }
        }
    }.flowOn(Dispatchers.IO)

    private fun getSignedParams(bvid: String, cid: Long, quality: Int): Map<String, String> {
        val navResp = apiService.getNavInfo().execute()
        val navData = navResp.body()?.data ?: throw Exception("密钥获取失败")
        val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
        val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
        val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)
        val params = TreeMap<String, Any>().apply {
            put("bvid", bvid); put("cid", cid); put("qn", quality); put(
            "fnval",
            "4048"
        ); put("fourk", "1")
        }
        val signed = BiliSigner.signParams(params, mixinKey)
        return signed.split("&").associate {
            val p = it.split("=")
            URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8")
        }
    }
}