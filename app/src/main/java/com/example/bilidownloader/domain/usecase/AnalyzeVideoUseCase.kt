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
 * 视频解析用例.
 *
 * 负责将用户输入的链接（短链、长链、文本混合）转换为可播放/下载的流媒体信息。
 * 核心流程：
 * 1. **链接清洗**：识别并解析 `b23.tv` 短链接重定向。
 * 2. **元数据获取**：获取视频标题、封面等基础信息并写入历史记录。
 * 3. **WBI 签名**：动态获取密钥并计算签名，以访问高权限的流地址接口。
 * 4. **流媒体筛选**：解析 DASH 格式，过滤移动端不友好的编码（如 AV1），提取杜比/Hi-Res 音轨。
 */
class AnalyzeVideoUseCase(
    private val historyRepository: HistoryRepository
) {
    // 专门处理重定向的轻量级 Client
    private val redirectClient = OkHttpClient.Builder().followRedirects(true).build()

    operator fun invoke(input: String): Flow<Resource<VideoAnalysisResult>> = flow {
        emit(Resource.Loading())

        try {
            // --- 1. 链接提取与短链还原 ---
            var bvid = LinkUtils.extractBvid(input)
            if (bvid == null) {
                // 尝试处理 b23.tv 短链接
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

            // --- 2. 获取基础元数据 (View 接口) ---
            // 使用 execute() 同步调用，因为我们在 Flow 的 IO 上下文中
            val viewResp = NetworkModule.biliService.getVideoView(bvid).execute()
            val detail = viewResp.body()?.data ?: throw Exception("无法获取视频信息")
            val cid = detail.pages[0].cid

            // 写入本地历史记录
            historyRepository.insert(
                HistoryEntity(
                    bvid = detail.bvid,
                    title = detail.title,
                    coverUrl = detail.pic,
                    uploader = detail.owner.name,
                    timestamp = System.currentTimeMillis()
                )
            )

            // --- 3. WBI 签名 (获取高画质流的前提) ---
            val navResp = NetworkModule.biliService.getNavInfo().execute()
            val navData = navResp.body()?.data ?: throw Exception("无法获取密钥")
            val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
            val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

            // --- 4. 构建参数并请求流地址 ---
            val params = TreeMap<String, Any>().apply {
                put("bvid", detail.bvid)
                put("cid", cid)
                put("qn", "127") // 请求 8K/4K 最高画质
                put("fnval", "4048") // 强制开启 DASH 格式 (fnval=16 | 64 | 2048 ...)
                put("fourk", "1")
            }
            val signedQuery = BiliSigner.signParams(params, mixinKey)

            // Retrofit @QueryMap 需要解码后的参数
            val queryMap = signedQuery.split("&").associate {
                val p = it.split("=")
                URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8")
            }

            val playResp = NetworkModule.biliService.getPlayUrl(queryMap).execute()
            val playData = playResp.body()?.data ?: throw Exception("无法获取播放列表: ${playResp.errorBody()?.string()}")

            // --- 5. 格式筛选与封装 ---
            val videoOpts = mutableListOf<FormatOption>()
            val audioOpts = mutableListOf<FormatOption>()

            if (playData.dash != null) {
                val durationInSeconds = if (playData.timelength != null && playData.timelength > 0) {
                    playData.timelength / 1000L
                } else {
                    180L
                }

                // A. 视频流处理 (过滤 AV1 编码，因其兼容性较差且转码慢)
                playData.dash.video.forEach { media ->
                    if (media.codecs?.startsWith("av01") == true) return@forEach

                    val qIndex = playData.accept_quality?.indexOf(media.id) ?: -1
                    val desc = if (qIndex >= 0 && qIndex < (playData.accept_description?.size ?: 0)) {
                        playData.accept_description?.get(qIndex) ?: "未知画质"
                    } else "画质 ${media.id}"

                    val codecSimple = when {
                        media.codecs?.startsWith("avc") == true -> "AVC"
                        media.codecs?.startsWith("hev") == true -> "HEVC"
                        else -> "MP4"
                    }

                    // 估算文件体积 (bps * seconds / 8)
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

                // B. 常规音频流
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

                // C. 杜比全景声 (Dolby Atmos)
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

                // D. 无损 Hi-Res (FLAC)
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

            // 去重并按码率倒序排列，优先展示高质量
            val finalVideoOpts = videoOpts.distinctBy { it.label }.sortedByDescending { it.bandwidth }
            val finalAudioOpts = audioOpts.distinctBy { it.label }.sortedByDescending { it.bandwidth }

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

    // region Internal Helpers

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

    // endregion
}