package com.example.bilidownloader.domain

import android.content.Context
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.data.repository.DownloadRepository
import com.example.bilidownloader.ui.state.FormatOption
import com.example.bilidownloader.utils.BiliSigner
import com.example.bilidownloader.utils.FFmpegHelper
import com.example.bilidownloader.utils.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.URLDecoder
import java.util.TreeMap

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
        try {
            val cacheDir = context.cacheDir

            // 1. 获取签名
            val qn = params.videoOption?.id ?: params.audioOption.id
            val queryMap = getSignedParams(params.bvid, params.cid, qn)

            // 2. 获取 API 数据
            val playResp = NetworkModule.biliService.getPlayUrl(queryMap).execute()
            val dash = playResp.body()?.data?.dash ?: throw Exception("无法获取流地址 (Dash为空)")

            // ================================================================
            // 【关键修改】文件名必须包含 ID 和 Codec，确保唯一性
            // ================================================================

            // 音频临时文件: BV_CID_30280_mp4a_audio.tmp
            val audioSuffix = "${params.audioOption.id}_${params.audioOption.codecs}"
            val audioTempName = "${params.bvid}_${params.cid}_${audioSuffix}_audio.tmp"
            val audioFile = File(cacheDir, audioTempName)

            // 视频临时文件 (仅当不是纯音频模式时生成)
            val videoTempName = if (!params.audioOnly && params.videoOption != null) {
                val vOpt = params.videoOption
                // 例如: BV_CID_80_avc_video.tmp
                "${params.bvid}_${params.cid}_${vOpt.id}_${vOpt.codecs}_video.tmp"
            } else {
                ""
            }

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
                ?: throw Exception("未找到音频流")

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
                if (!success) throw Exception("音频处理失败")

                StorageHelper.saveAudioToMusic(context, outAudio, "Bili_${params.bvid}$suffix")

                outAudio.delete()
                if (audioFile.exists()) audioFile.delete() // 下载完删除临时文件
                emit(Resource.Success("音频已保存"))
            }
            // ================================================================
            // 分支 B: 视频 + 音频
            // ================================================================
            else {
                val vOpt = params.videoOption!!

                val videoUrl: String = dash.video.find { it.id == vOpt.id && it.codecs == vOpt.codecs }?.baseUrl
                    ?: dash.video.firstOrNull()?.baseUrl
                    ?: throw Exception("视频流丢失")

                val foundAudioUrl: String = dash.audio?.find { it.id == params.audioOption.id }?.baseUrl
                    ?: dash.audio?.firstOrNull()?.baseUrl
                    ?: throw Exception("音频流丢失")

                val videoFile = File(cacheDir, videoTempName)
                val outMp4 = File(cacheDir, "${params.bvid}_${System.currentTimeMillis()}.mp4") // 最终文件名可以用时间戳避免冲突

                // 下载视频
                downloadRepository.downloadFile(videoUrl, videoFile).collect { p ->
                    emit(Resource.Loading(progress = p * 0.45f, data = "正在下载视频流..."))
                }

                // 下载音频
                downloadRepository.downloadFile(foundAudioUrl, audioFile).collect { p ->
                    emit(Resource.Loading(progress = 0.45f + p * 0.45f, data = "正在下载音频流..."))
                }

                // 合并
                emit(Resource.Loading(progress = 0.9f, data = "正在合并音视频..."))
                val success = FFmpegHelper.mergeVideoAudio(videoFile, audioFile, outMp4)
                if (!success) throw Exception("合并失败")

                emit(Resource.Loading(progress = 0.98f, data = "正在保存..."))
                StorageHelper.saveVideoToGallery(context, outMp4, "Bili_${params.bvid}.mp4")

                // 成功后才删除临时文件
                videoFile.delete()
                audioFile.delete()
                outMp4.delete()

                emit(Resource.Success("视频已保存到相册"))
            }

        } catch (e: Exception) {
            e.printStackTrace()
            throw e
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