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

            // 1. 获取签名与 API 请求 (保持不变)
            val qn = params.videoOption?.id ?: params.audioOption.id
            val queryMap = getSignedParams(params.bvid, params.cid, qn)
            val playResp = NetworkModule.biliService.getPlayUrl(queryMap).execute()
            val dash = playResp.body()?.data?.dash ?: throw Exception("无法获取流地址 (Dash为空)")

            // 【核心修改 1】使用固定的临时文件名 (BVID + CID + 类型)
            // 这样暂停后再回来，文件名一样，Repository 才能发现并续传
            val audioTempName = "${params.bvid}_${params.cid}_audio.tmp"
            val videoTempName = "${params.bvid}_${params.cid}_video.tmp"

            val audioFile = File(cacheDir, audioTempName)

            // ================================================================
            // 分支 A: 仅下载音频
            // ================================================================
            if (params.audioOnly) {
                // ... 匹配 URL 逻辑保持不变 ...
                val foundAudioUrl: String = if (params.audioOption.codecs == "flac") {
                    dash.flac?.audio?.baseUrl
                } else {
                    null
                } ?: dash.audio?.find { it.id == params.audioOption.id }?.baseUrl
                ?: dash.audio?.firstOrNull()?.baseUrl
                ?: throw Exception("未找到音频流")

                val suffix = if (params.audioOption.codecs == "flac") ".flac" else ".mp3"
                val outAudio = File(cacheDir, "${params.bvid}_out$suffix")

                // 下载音频
                emit(Resource.Loading(progress = 0f, data = "正在下载音频..."))
                downloadRepository.downloadFile(foundAudioUrl, audioFile).collect { progress ->
                    emit(Resource.Loading(progress = progress * 0.8f, data = "正在下载音频..."))
                }

                // 处理与保存 (FFmpeg)
                emit(Resource.Loading(progress = 0.8f, data = "正在处理音频..."))
                val success = if (params.audioOption.codecs == "flac") {
                    FFmpegHelper.remuxToFlac(audioFile, outAudio)
                } else {
                    FFmpegHelper.convertAudioToMp3(audioFile, outAudio)
                }
                if (!success) throw Exception("音频处理失败")

                StorageHelper.saveAudioToMusic(context, outAudio, "Bili_${params.bvid}$suffix")

                // 清理临时文件
                outAudio.delete()
                if (audioFile.exists()) audioFile.delete()

                emit(Resource.Success("音频已保存到音乐库"))
            }
            // ================================================================
            // 分支 B: 视频 + 音频
            // ================================================================
            else {
                val vOpt = params.videoOption!!

                // ... 匹配 URL 逻辑保持不变 ...
                val videoUrl: String = dash.video.find { it.id == vOpt.id && it.codecs == vOpt.codecs }?.baseUrl
                    ?: dash.video.firstOrNull()?.baseUrl
                    ?: throw Exception("视频流丢失")

                val foundAudioUrl: String = dash.audio?.find { it.id == params.audioOption.id }?.baseUrl
                    ?: dash.audio?.firstOrNull()?.baseUrl
                    ?: throw Exception("音频流丢失")

                // 【核心修改 2】使用上面定义的固定 Video 文件名
                val videoFile = File(cacheDir, videoTempName)
                val outMp4 = File(cacheDir, "${params.bvid}_final.mp4")

                // 1. 下载视频 (0% -> 45%)
                // 注意：这里 flow 可能会立即从 0.5 (50%) 开始，如果文件已经下载了一半
                downloadRepository.downloadFile(videoUrl, videoFile).collect { p ->
                    emit(Resource.Loading(progress = p * 0.45f, data = "正在下载视频流..."))
                }

                // 2. 下载音频 (45% -> 90%)
                downloadRepository.downloadFile(foundAudioUrl, audioFile).collect { p ->
                    emit(Resource.Loading(progress = 0.45f + p * 0.45f, data = "正在下载音频流..."))
                }

                // 3. 合并
                emit(Resource.Loading(progress = 0.9f, data = "正在合并音视频..."))
                val success = FFmpegHelper.mergeVideoAudio(videoFile, audioFile, outMp4)
                if (!success) throw Exception("FFmpeg 合并失败")

                // 4. 保存
                emit(Resource.Loading(progress = 0.98f, data = "正在保存到相册..."))
                StorageHelper.saveVideoToGallery(context, outMp4, "Bili_${params.bvid}.mp4")

                // 清理：只有成功合并后才删除临时文件
                // 这样如果中途失败或暂停，临时文件还在，下次能续传
                videoFile.delete()
                audioFile.delete()
                outMp4.delete()

                emit(Resource.Success("视频已保存到相册"))
            }

        } catch (e: Exception) {
            e.printStackTrace()
            // 抛出异常前，不要删除临时文件，以便下次重试
            throw e
        }
    }.flowOn(Dispatchers.IO)

    // ... getSignedParams 保持不变 ...
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