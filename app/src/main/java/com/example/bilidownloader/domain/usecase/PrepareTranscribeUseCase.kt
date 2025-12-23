package com.example.bilidownloader.domain.usecase

import android.content.Context
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.data.repository.DownloadRepository
import com.example.bilidownloader.core.util.BiliSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.URLDecoder
import java.util.TreeMap

/**
 * 转写预处理用例.
 *
 * 职责：从 B 站视频中提取纯音频轨道，并将其下载为本地临时文件。
 * 这是连接 B 站视频源与阿里云 ASR 服务的桥梁。
 */
class PrepareTranscribeUseCase(
    private val context: Context,
    private val downloadRepository: DownloadRepository
) {
    data class Params(val bvid: String, val cid: Long)

    operator fun invoke(params: Params): Flow<Resource<String>> = flow {
        emit(Resource.Loading(progress = 0f, data = "正在准备音频..."))

        try {
            // 1. 获取低画质流 (qn=16/80) 即可，因为我们要的是音频
            val queryMap = getSignedParams(params.bvid, params.cid, 80)

            val playResp = NetworkModule.biliService.getPlayUrl(queryMap).execute()
            val data = playResp.body()?.data

            // 优先提取 DASH 音频流，若无则使用 MP4 直链
            val audioUrl: String = data?.dash?.audio?.firstOrNull()?.baseUrl
                ?: data?.durl?.firstOrNull()?.url
                ?: throw Exception("提取音频失败：未找到音频流")

            val tempFile = File(context.cacheDir, "trans_${System.currentTimeMillis()}.m4a")

            // 2. 下载音频到 Cache 目录
            downloadRepository.downloadFile(audioUrl, tempFile).collect { progress ->
                emit(Resource.Loading(
                    progress = progress,
                    data = "提取音频中... ${(progress * 100).toInt()}%"
                ))
            }

            // 3. 返回本地文件绝对路径供 OSS 上传使用
            emit(Resource.Success(tempFile.absolutePath))

        } catch (e: Exception) {
            e.printStackTrace()
            emit(Resource.Error("提取失败: ${e.message}"))
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