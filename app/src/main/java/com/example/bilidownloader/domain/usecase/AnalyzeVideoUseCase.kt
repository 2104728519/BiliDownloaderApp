package com.example.bilidownloader.domain.usecase

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.data.database.HistoryEntity
import com.example.bilidownloader.data.repository.HistoryRepository
import com.example.bilidownloader.ui.state.FormatOption
import com.example.bilidownloader.core.util.BiliSigner
import com.example.bilidownloader.core.util.LinkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.TreeMap
import java.util.regex.Pattern

/**
 * 视频解析用例 (业务逻辑核心)
 * 职责：
 * 1. 识别并清洗链接 (处理短链接)
 * 2. 获取视频详情和 WBI 签名
 * 3. 筛选可用的音视频流 (过滤 AV1，处理 Dash/Dolby/Hi-Res)
 * 4. 写入历史记录
 */
class AnalyzeVideoUseCase(
    private val historyRepository: HistoryRepository
) {
    // 短链接重定向客户端
    private val redirectClient = OkHttpClient.Builder().followRedirects(true).build()

    operator fun invoke(input: String): Flow<Resource<VideoAnalysisResult>> = flow {
        emit(Resource.Loading())

        try {
            // --- 1. 提取 BV 号 ---
            var bvid = LinkUtils.extractBvid(input)
            if (bvid == null) {
                val url = findUrl(input)
                if (url != null && url.contains("b23.tv")) {
                    val realUrl = resolveShortLink(url)
                    bvid = LinkUtils.extractBvid(realUrl)
                }
            }
            if (bvid == null) {
                emit(Resource.Error("没找到 BV 号，请检查链接"))
                return@flow
            }

            // --- 2. 获取基本信息 ---
            // 使用 NetworkModule 直接调用
            val viewResp = NetworkModule.biliService.getVideoView(bvid).execute()
            val detail = viewResp.body()?.data ?: throw Exception("无法获取视频信息")
            val cid = detail.pages[0].cid

            // 写入历史
            historyRepository.insert(
                HistoryEntity(
                    bvid = detail.bvid,
                    title = detail.title,
                    coverUrl = detail.pic,
                    uploader = detail.owner.name,
                    timestamp = System.currentTimeMillis()
                )
            )

            // --- 3. WBI 签名 (获取密钥) ---
            val navResp = NetworkModule.biliService.getNavInfo().execute()
            val navData = navResp.body()?.data ?: throw Exception("无法获取密钥")
            val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
            val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

            // --- 4. 构建参数 ---
            val params = TreeMap<String, Any>().apply {
                put("bvid", detail.bvid)
                put("cid", cid)
                put("qn", "127") // 请求最高画质
                put("fnval", "4048") // 开启 Dash
                put("fourk", "1")
            }
            val signedQuery = BiliSigner.signParams(params, mixinKey)
            val queryMap = signedQuery.split("&").associate {
                val p = it.split("=")
                URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8")
            }

            // --- 5. 获取流媒体信息 ---
            val playResp = NetworkModule.biliService.getPlayUrl(queryMap).execute()
            val playData = playResp.body()?.data ?: throw Exception("无法获取播放列表: ${playResp.errorBody()?.string()}")

            val videoOpts = mutableListOf<FormatOption>()
            val audioOpts = mutableListOf<FormatOption>()

            if (playData.dash != null) {
                val durationInSeconds = if (playData.timelength != null && playData.timelength > 0) {
                    playData.timelength / 1000L
                } else {
                    180L
                }

                // A. 解析视频流 (过滤 AV1)
                playData.dash.video.forEach { media ->
                    if (media.codecs?.startsWith("av01") == true) return@forEach // 过滤 AV1

                    val qIndex = playData.accept_quality?.indexOf(media.id) ?: -1
                    val desc = if (qIndex >= 0 && qIndex < (playData.accept_description?.size ?: 0)) {
                        playData.accept_description?.get(qIndex) ?: "未知画质"
                    } else "画质 ${media.id}"

                    val codecSimple = when {
                        media.codecs?.startsWith("avc") == true -> "AVC"
                        media.codecs?.startsWith("hev") == true -> "HEVC"
                        else -> "MP4"
                    }

                    val estimatedSize = (media.bandwidth * durationInSeconds / 8)
                    videoOpts.add(FormatOption(
                        id = media.id,
                        label = "$desc ($codecSimple) - 约 ${formatSize(estimatedSize)}",
                        description = desc,
                        codecs = media.codecs,
                        bandwidth = media.bandwidth,
                        estimatedSize = estimatedSize
                    ))
                }

                // B. 解析常规音频
                playData.dash.audio?.forEach { media ->
                    val idMap = mapOf(30280 to "192K", 30232 to "132K", 30216 to "64K")
                    val name = idMap[media.id] ?: "普通音质 ${media.id}"
                    val estimatedSize = (media.bandwidth * durationInSeconds / 8)
                    audioOpts.add(FormatOption(
                        id = media.id,
                        label = "$name (AAC) - 约 ${formatSize(estimatedSize)}",
                        description = name,
                        codecs = media.codecs,
                        bandwidth = media.bandwidth,
                        estimatedSize = estimatedSize
                    ))
                }

                // C. 解析杜比
                playData.dash.dolby?.audio?.forEach { media ->
                    val estimatedSize = (media.bandwidth * durationInSeconds / 8)
                    audioOpts.add(FormatOption(
                        id = media.id,
                        label = "杜比全景声 (Dolby) - 约 ${formatSize(estimatedSize)}",
                        description = "杜比全景声",
                        codecs = media.codecs,
                        bandwidth = media.bandwidth,
                        estimatedSize = estimatedSize
                    ))
                }

                // D. 解析 Hi-Res (FLAC)
                val flacMedia = playData.dash.flac?.audio
                if (flacMedia != null) {
                    val estimatedSize = (flacMedia.bandwidth * durationInSeconds / 8)
                    audioOpts.add(FormatOption(
                        id = flacMedia.id,
                        label = "无损 Hi-Res (FLAC) - 约 ${formatSize(estimatedSize)}",
                        description = "无损 Hi-Res",
                        codecs = "flac",
                        bandwidth = flacMedia.bandwidth,
                        estimatedSize = estimatedSize
                    ))
                }
            }

            // 去重并排序
            val finalVideoOpts = videoOpts.distinctBy { it.label }.sortedByDescending { it.bandwidth }
            val finalAudioOpts = audioOpts.distinctBy { it.label }.sortedByDescending { it.bandwidth }

            // 发送成功结果
            emit(Resource.Success(
                VideoAnalysisResult(
                    detail = detail,
                    videoFormats = finalVideoOpts,
                    audioFormats = finalAudioOpts
                )
            ))

        } catch (e: Exception) {
            e.printStackTrace()
            emit(Resource.Error("解析错误: ${e.message}"))
        }
    }.flowOn(Dispatchers.IO)

    // --- 内部辅助方法 (从 ViewModel 搬过来的) ---

    private fun findUrl(text: String): String? {
        val matcher = Pattern.compile("http[s]?://\\S+").matcher(text)
        return if (matcher.find()) matcher.group() else null
    }

    private fun resolveShortLink(shortUrl: String): String {
        try {
            val request = Request.Builder().url(shortUrl).head().build()
            val response = redirectClient.newCall(request).execute()
            val finalUrl = response.request.url.toString()
            response.close()
            return finalUrl
        } catch (e: Exception) {
            return shortUrl
        }
    }

    private fun formatSize(bytes: Long): String {
        if (bytes < 0) return "N/A"
        val mb = bytes / 1024.0 / 1024.0
        return String.format("%.1fMB", mb)
    }
}