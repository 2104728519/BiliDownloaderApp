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

/**
 * 视频下载用例
 * 职责：
 * 1. 刷新 WBI 签名 (防链接过期)
 * 2. 精确匹配用户选择的视频/音频流 (处理 FLAC/Dolby)
 * 3. 协调下载任务，并汇报精确进度
 * 4. 调用 FFmpeg 进行合并或格式转换
 * 5. 保存到系统相册/音乐库
 */
class DownloadVideoUseCase(
    private val context: Context,
    private val downloadRepository: DownloadRepository
) {

    /**
     * UseCase 的输入参数模型
     */
    data class Params(
        val bvid: String,
        val cid: Long,
        val videoOption: FormatOption?,
        val audioOption: FormatOption,
        val audioOnly: Boolean
    )

    /**
     * 执行下载任务
     * @return 返回一个 Flow，持续发出 Resource 状态 (Loading with progress, Success, Error)
     */
    operator fun invoke(params: Params): Flow<Resource<String>> = flow {
        // 初始状态
        emit(Resource.Loading(progress = 0f, data = "准备下载..."))

        try {
            val cacheDir = context.cacheDir
            // 1. 获取签名后的参数
            val qn = params.videoOption?.id ?: params.audioOption.id
            val queryMap = getSignedParams(params.bvid, params.cid, qn)

            // 2. 获取实时播放地址
            val playResp = NetworkModule.biliService.getPlayUrl(queryMap).execute()
            val dash = playResp.body()?.data?.dash ?: throw Exception("无法获取流地址 (Dash为空)")

            val audioFile = File(cacheDir, "temp_audio_${System.currentTimeMillis()}.m4s")

            // ================================================================
            // 分支 A: 仅下载音频
            // ================================================================
            if (params.audioOnly) {
                // 匹配音频流
                val foundAudioUrl: String = if (params.audioOption.codecs == "flac") {
                    dash.flac?.audio?.baseUrl
                } else {
                    null
                } ?: dash.audio?.find { it.id == params.audioOption.id }?.baseUrl
                ?: dash.audio?.firstOrNull()?.baseUrl
                ?: throw Exception("未找到音频流")

                val suffix = if (params.audioOption.codecs == "flac") ".flac" else ".mp3"
                val outAudio = File(cacheDir, "${params.bvid}_out$suffix")

                // 下载音频 (占总进度 0% -> 80%)
                emit(Resource.Loading(progress = 0f, data = "正在下载音频..."))
                downloadRepository.downloadFile(foundAudioUrl, audioFile).collect { progress ->
                    emit(Resource.Loading(progress = progress * 0.8f, data = "正在下载音频..."))
                }

                // 格式转换 (占总进度 80% -> 95%)
                emit(Resource.Loading(progress = 0.8f, data = "正在处理音频 (FFmpeg)..."))
                val success = if (params.audioOption.codecs == "flac") {
                    FFmpegHelper.remuxToFlac(audioFile, outAudio)
                } else {
                    FFmpegHelper.convertAudioToMp3(audioFile, outAudio)
                }
                if (!success) throw Exception("音频处理失败")

                // 保存文件 (占总进度 95% -> 100%)
                emit(Resource.Loading(progress = 0.95f, data = "正在保存到音乐库..."))
                StorageHelper.saveAudioToMusic(context, outAudio, "Bili_${params.bvid}$suffix")

                outAudio.delete()
                emit(Resource.Success("音频已保存到音乐库"))

            }
            // ================================================================
            // 分支 B: 视频 + 音频
            // ================================================================
            else {
                val vOpt = params.videoOption!!

                // 匹配视频流
                val videoUrl: String = dash.video.find { it.id == vOpt.id && it.codecs == vOpt.codecs }?.baseUrl
                    ?: dash.video.firstOrNull()?.baseUrl
                    ?: throw Exception("视频流丢失")

                // 匹配音频流
                val foundAudioUrl: String = dash.audio?.find { it.id == params.audioOption.id }?.baseUrl
                    ?: dash.audio?.firstOrNull()?.baseUrl
                    ?: throw Exception("音频流丢失")

                val videoFile = File(cacheDir, "temp_video_${System.currentTimeMillis()}.m4s")
                val outMp4 = File(cacheDir, "${params.bvid}_final.mp4")

                // 下载视频 (占总进度 0% -> 45%)
                emit(Resource.Loading(progress = 0f, data = "正在下载视频流..."))
                downloadRepository.downloadFile(videoUrl, videoFile).collect { p ->
                    emit(Resource.Loading(progress = p * 0.45f, data = "正在下载视频流..."))
                }

                // 下载音频 (占总进度 45% -> 90%)
                emit(Resource.Loading(progress = 0.45f, data = "正在下载音频流..."))
                downloadRepository.downloadFile(foundAudioUrl, audioFile).collect { p ->
                    emit(Resource.Loading(progress = 0.45f + p * 0.45f, data = "正在下载音频流..."))
                }

                // 合并 (占总进度 90% -> 98%)
                emit(Resource.Loading(progress = 0.9f, data = "正在合并音视频..."))
                val success = FFmpegHelper.mergeVideoAudio(videoFile, audioFile, outMp4)
                if (!success) throw Exception("FFmpeg 合并失败")

                // 保存 (占总进度 98% -> 100%)
                emit(Resource.Loading(progress = 0.98f, data = "正在保存到相册..."))
                StorageHelper.saveVideoToGallery(context, outMp4, "Bili_${params.bvid}.mp4")

                videoFile.delete()
                outMp4.delete()
                emit(Resource.Success("视频已保存到相册"))
            }

            // 公共清理
            if (audioFile.exists()) audioFile.delete()

        } catch (e: Exception) {
            e.printStackTrace()
            emit(Resource.Error("下载任务失败: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 私有辅助方法：获取 WBI 签名后的参数
     */
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