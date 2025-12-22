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
 * 转写准备用例
 * 职责：提取视频的音频轨道，下载为临时文件，供阿里云转写服务使用
 */
class PrepareTranscribeUseCase(
    private val context: Context,
    private val downloadRepository: DownloadRepository
) {

    // 输入参数：需要 BVID 和 CID
    data class Params(val bvid: String, val cid: Long)

    // 返回：下载好的文件绝对路径
    operator fun invoke(params: Params): Flow<Resource<String>> = flow {
        // 【修复】使用新的构造函数，明确指定 data 参数
        emit(Resource.Loading(progress = 0f, data = "正在准备音频..."))

        try {
            // 1. 获取签名后的参数 (普通画质 80 即可)
            val queryMap = getSignedParams(params.bvid, params.cid, 80)

            // 2. 请求播放地址
            val playResp = NetworkModule.biliService.getPlayUrl(queryMap).execute()
            val data = playResp.body()?.data

            // 优先找 Dash 音频，没有则找 Durl
            val audioUrl: String = data?.dash?.audio?.firstOrNull()?.baseUrl
                ?: data?.durl?.firstOrNull()?.url
                ?: throw Exception("提取音频失败：未找到音频流")

            // 3. 准备临时文件
            val tempFile = File(context.cacheDir, "trans_${System.currentTimeMillis()}.m4a")

            // 4. 下载
            downloadRepository.downloadFile(audioUrl, tempFile).collect { progress ->
                // 【修复】使用新的构造函数，传入 progress 和 data
                emit(Resource.Loading(
                    progress = progress,
                    data = "提取音频中... ${(progress * 100).toInt()}%"
                ))
            }

            // 5. 成功返回路径
            emit(Resource.Success(tempFile.absolutePath))

        } catch (e: Exception) {
            e.printStackTrace()
            emit(Resource.Error("提取失败: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    // 复用签名逻辑
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