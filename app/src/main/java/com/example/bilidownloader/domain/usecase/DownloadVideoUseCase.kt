package com.example.bilidownloader.domain.usecase

import android.content.Context
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.data.repository.DownloadRepository
import com.example.bilidownloader.ui.state.FormatOption
import com.example.bilidownloader.core.util.BiliSigner
import com.example.bilidownloader.core.util.FFmpegHelper
import com.example.bilidownloader.core.util.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.URLDecoder
import java.util.TreeMap

/**
 * 视频下载用例 (修复版)
 * 1. 修复了 codecs 可能为空导致的编译错误
 * 2. 修复了异常导致协程崩溃的问题
 * 3. 增加了对非标准视频流的容错处理
 */
class DownloadVideoUseCase(
    private val context: Context,
    private val downloadRepository: DownloadRepository
) {
    data class Params(
        val bvid: String,
        val cid: Long,
        val videoOption: FormatOption?,
        val audioOption: FormatOption,
        val audioOnly: Boolean
    )

    operator fun invoke(params: Params): Flow<Resource<String>> = flow {
        emit(Resource.Loading(progress = 0f, data = "准备下载..."))

        var videoFile: File? = null
        var audioFile: File? = null
        var outMp4: File? = null

        try {
            val cacheDir = context.cacheDir

            // 1. 获取签名与 API 请求
            val qn = params.videoOption?.id ?: params.audioOption.id
            val queryMap = getSignedParams(params.bvid, params.cid, qn)

            val playResp = NetworkModule.biliService.getPlayUrl(queryMap).execute()
            val responseBody = playResp.body()

            if (responseBody?.code != 0) {
                throw Exception("API 错误: ${responseBody?.message} (code: ${responseBody?.code})")
            }

            val data = responseBody.data ?: throw Exception("API 返回数据为空")

            if (data.dash == null) {
                throw Exception("该视频格式(durl)暂不支持下载，仅支持 DASH 格式")
            }
            val dash = data.dash

            // ================================================================
            // 准备文件名 (修复 codecs 可能为空的问题)
            // ================================================================
            // 【修复点 1】给 codecs 加默认值 "unk" (unknown)
            val safeAudioCodec = params.audioOption.codecs ?: "unk"
            val audioSuffix = "${params.audioOption.id}_${safeAudioCodec.replace("[^a-zA-Z0-9]".toRegex(), "")}"
            val audioTempName = "${params.bvid}_${params.cid}_${audioSuffix}_audio.tmp"
            audioFile = File(cacheDir, audioTempName)

            // ================================================================
            // 分支 A: 仅下载音频
            // ================================================================
            if (params.audioOnly) {
                val foundAudioUrl: String = if (params.audioOption.codecs == "flac") {
                    dash.flac?.audio?.baseUrl
                } else {
                    null
                } ?: dash.audio?.find { it.id == params.audioOption.id }?.baseUrl
                ?: dash.audio?.firstOrNull()?.baseUrl
                ?: throw Exception("未找到可用的音频流")

                val suffix = if (params.audioOption.codecs == "flac") ".flac" else ".mp3"
                val outAudio = File(cacheDir, "${params.bvid}_out$suffix")

                emit(Resource.Loading(progress = 0f, data = "正在下载音频..."))
                downloadRepository.downloadFile(foundAudioUrl, audioFile).collect { progress ->
                    emit(Resource.Loading(progress = progress * 0.8f, data = "正在下载音频..."))
                }

                emit(Resource.Loading(progress = 0.8f, data = "正在转码..."))
                val success = if (params.audioOption.codecs == "flac") {
                    FFmpegHelper.remuxToFlac(audioFile, outAudio)
                } else {
                    FFmpegHelper.convertAudioToMp3(audioFile, outAudio)
                }
                if (!success) throw Exception("音频转码失败")

                StorageHelper.saveAudioToMusic(context, outAudio, "Bili_${params.bvid}$suffix")

                outAudio.delete()
                audioFile.delete()
                emit(Resource.Success("音频已保存到音乐库"))
            }
            // ================================================================
            // 分支 B: 视频 + 音频
            // ================================================================
            else {
                val vOpt = params.videoOption ?: throw Exception("视频参数丢失")

                val videoUrl: String = dash.video.find { it.id == vOpt.id && it.codecs == vOpt.codecs }?.baseUrl
                    ?: dash.video.find { it.id == vOpt.id }?.baseUrl
                    ?: dash.video.firstOrNull()?.baseUrl
                    ?: throw Exception("未找到视频流")

                val foundAudioUrl: String = dash.audio?.find { it.id == params.audioOption.id }?.baseUrl
                    ?: dash.audio?.firstOrNull()?.baseUrl
                    ?: throw Exception("未找到音频流")

                // 视频临时文件名
                // 【修复点 2】给 codecs 加默认值 "unk"
                val safeVideoCodec = vOpt.codecs ?: "unk"
                val videoSuffix = "${vOpt.id}_${safeVideoCodec.replace("[^a-zA-Z0-9]".toRegex(), "")}"
                val videoTempName = "${params.bvid}_${params.cid}_${videoSuffix}_video.tmp"
                videoFile = File(cacheDir, videoTempName)

                outMp4 = File(cacheDir, "${params.bvid}_${System.currentTimeMillis()}.mp4")

                // 1. 下载视频
                downloadRepository.downloadFile(videoUrl, videoFile).collect { p ->
                    emit(Resource.Loading(progress = p * 0.45f, data = "正在下载视频流..."))
                }

                // 2. 下载音频
                downloadRepository.downloadFile(foundAudioUrl, audioFile).collect { p ->
                    emit(Resource.Loading(progress = 0.45f + p * 0.45f, data = "正在下载音频流..."))
                }

                // 3. 合并
                emit(Resource.Loading(progress = 0.9f, data = "正在合并音视频..."))
                val success = FFmpegHelper.mergeVideoAudio(videoFile, audioFile, outMp4)
                if (!success) throw Exception("FFmpeg 合并失败")

                // 4. 保存
                emit(Resource.Loading(progress = 0.98f, data = "正在保存..."))
                StorageHelper.saveVideoToGallery(context, outMp4, "Bili_${params.bvid}.mp4")

                // 清理
                videoFile.delete()
                audioFile.delete()
                outMp4.delete()

                emit(Resource.Success("视频已保存到相册"))
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // 捕获异常发送给 UI，避免 Crash
            emit(Resource.Error("下载出错: ${e.message}"))
            outMp4?.delete()
        }
    }.flowOn(Dispatchers.IO)

    private fun getSignedParams(bvid: String, cid: Long, quality: Int): Map<String, String> {
        val navResp = NetworkModule.biliService.getNavInfo().execute()
        val navData = navResp.body()?.data ?: throw Exception("无法获取密钥")
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
        val signedQuery = BiliSigner.signParams(params, mixinKey)
        return signedQuery.split("&").associate {
            val p = it.split("=")
            URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8")
        }
    }
}