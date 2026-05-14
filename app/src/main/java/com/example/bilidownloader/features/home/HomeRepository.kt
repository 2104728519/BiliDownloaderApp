package com.example.bilidownloader.features.home

import android.content.Context
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.database.HistoryDao
import com.example.bilidownloader.core.database.HistoryEntity
import com.example.bilidownloader.core.manager.CookieManager
import com.example.bilidownloader.core.model.*
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.core.util.BiliSigner
import com.example.bilidownloader.core.util.LinkUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLDecoder
import java.util.TreeMap
import java.util.regex.Pattern

/**
 * 首页核心仓库.
 */
class HomeRepository(
    private val context: Context,
    private val historyDao: HistoryDao
) {
    private val apiService = NetworkModule.biliService
    private val redirectClient = OkHttpClient.Builder().followRedirects(true).build()

    suspend fun analyzeVideo(input: String): Resource<VideoAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            var bvid = LinkUtils.extractBvid(input)
            if (bvid == null) {
                val url = findUrl(input)
                if (url != null && url.contains("b23.tv")) {
                    val realUrl = resolveShortLink(url)
                    bvid = LinkUtils.extractBvid(realUrl)
                }
            }
            if (bvid == null) {
                return@withContext Resource.Error("没找到 BV 号，请检查链接")
            }

            val viewResp = apiService.getVideoView(bvid).execute()
            val detail = viewResp.body()?.data ?: return@withContext Resource.Error("无法获取视频信息")

            val firstPage =
                detail.pages.firstOrNull() ?: return@withContext Resource.Error("该视频没有分P信息")

            historyDao.insertHistory(
                HistoryEntity(
                    bvid = detail.bvid,
                    title = detail.title,
                    coverUrl = detail.pic,
                    uploader = detail.owner.name,
                    timestamp = System.currentTimeMillis()
                )
            )

            val formatsResult = fetchVideoFormats(detail.bvid, firstPage.cid)
            if (formatsResult is Resource.Error) {
                return@withContext Resource.Error(formatsResult.message ?: "获取流地址失败")
            }

            val formats = formatsResult.data!!

            Resource.Success(
                VideoAnalysisResult(
                    detail = detail,
                    videoFormats = formats.first,
                    audioFormats = formats.second,
                    durationSeconds = formats.third
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
            Resource.Error("解析错误: ${e.message}")
        }
    }

    suspend fun fetchVideoFormats(
        bvid: String,
        cid: Long
    ): Resource<Triple<List<FormatOption>, List<FormatOption>, Long>> =
        withContext(Dispatchers.IO) {
        try {
            val navResp = apiService.getNavInfo().execute()
            val navData = navResp.body()?.data ?: return@withContext Resource.Error("无法获取密钥")
            val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
            val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

            val params = TreeMap<String, Any>().apply {
                put("bvid", bvid)
                put("cid", cid)
                put("qn", "127")
                put("fnval", "4048")
                put("fourk", "1")
            }
            val signedQuery = BiliSigner.signParams(params, mixinKey)

            val queryMap = signedQuery.split("&").associate {
                val p = it.split("=")
                URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8")
            }

            val playResp = apiService.getPlayUrl(queryMap).execute()
            val playData =
                playResp.body()?.data ?: return@withContext Resource.Error("无法获取播放列表")

            val videoOpts = mutableListOf<FormatOption>()
            val audioOpts = mutableListOf<FormatOption>()
            val durationInSeconds = if (playData.timelength != null && playData.timelength > 0) {
                playData.timelength / 1000L
            } else {
                180L
            }

            if (playData.dash != null) {
                playData.dash.video.forEach { media ->
                    val qIndex = playData.accept_quality?.indexOf(media.id) ?: -1
                    val desc = if (qIndex >= 0 && qIndex < (playData.accept_description?.size ?: 0)) {
                        playData.accept_description?.get(qIndex) ?: "未知画质"
                    } else "画质 ${media.id}"

                    val codecSimple = when {
                        media.codecs?.startsWith("avc") == true -> "AVC"
                        media.codecs?.startsWith("hev") == true -> "HEVC"
                        media.codecs?.startsWith("av01") == true -> "AV1"
                        else -> "MP4"
                    }

                    val estimatedSize = (media.bandwidth * durationInSeconds / 8)
                    videoOpts.add(
                        FormatOption(
                            id = media.id,
                            label = "$desc ($codecSimple) - 约 ${formatSize(estimatedSize)}",
                            description = desc,
                            codecs = media.codecs,
                            bandwidth = media.bandwidth,
                            estimatedSize = estimatedSize
                        )
                    )
                }

                playData.dash.audio?.forEach { media ->
                    val idMap = mapOf(30280 to "192K", 30232 to "132K", 30216 to "64K")
                    val name = idMap[media.id] ?: "普通音质 ${media.id}"
                    val estimatedSize = (media.bandwidth * durationInSeconds / 8)
                    audioOpts.add(
                        FormatOption(
                            id = media.id,
                            label = "$name (AAC) - 约 ${formatSize(estimatedSize)}",
                            description = name,
                            codecs = media.codecs,
                            bandwidth = media.bandwidth,
                            estimatedSize = estimatedSize
                        )
                    )
                }

                playData.dash.dolby?.audio?.forEach { media ->
                    val estimatedSize = (media.bandwidth * durationInSeconds / 8)
                    audioOpts.add(
                        FormatOption(
                            id = media.id,
                            label = "杜比全景声 (Dolby) - 约 ${formatSize(estimatedSize)}",
                            description = "杜比全景声",
                            codecs = media.codecs,
                            bandwidth = media.bandwidth,
                            estimatedSize = estimatedSize
                        )
                    )
                }

                val flacMedia = playData.dash.flac?.audio
                if (flacMedia != null) {
                    val estimatedSize = (flacMedia.bandwidth * durationInSeconds / 8)
                    audioOpts.add(
                        FormatOption(
                            id = flacMedia.id,
                            label = "无损 Hi-Res (FLAC) - 约 ${formatSize(estimatedSize)}",
                            description = "无损 Hi-Res",
                            codecs = "flac",
                            bandwidth = flacMedia.bandwidth,
                            estimatedSize = estimatedSize
                        )
                    )
                }
            }

            val finalVideoOpts = videoOpts.distinctBy { it.label }.sortedByDescending { it.bandwidth }
            val finalAudioOpts = audioOpts.distinctBy { it.label }.sortedByDescending { it.bandwidth }

            Resource.Success(Triple(finalVideoOpts, finalAudioOpts, durationInSeconds))
        } catch (e: Exception) {
            e.printStackTrace()
            Resource.Error("获取流地址失败: ${e.message}")
        }
    }

    suspend fun reportProgress(aid: Long, cid: Long) {
        try {
            val csrf = CookieManager.getCookieValue(context, "bili_jct") ?: return
            apiService.reportHistory(aid, cid, progress = 10, csrf = csrf)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun fetchCloudHistory(cursor: HistoryCursor?): Resource<Pair<List<CloudHistoryItem>, HistoryCursor?>> =
        withContext(Dispatchers.IO) {
            try {
                val response = apiService.getHistory(
                    viewAt = cursor?.view_at ?: 0,
                    max = cursor?.max ?: 0
                )
                if (response.code == 0) {
                    val data = response.data
                    val list = data?.list ?: emptyList()
                    val nextCursor = data?.cursor
                    val hasMore = !list.isNullOrEmpty() && (nextCursor?.max ?: 0) > 0
                    Resource.Success(Pair(list, if (hasMore) nextCursor else null))
                } else {
                    val errorMsg = if (response.code == -101) "请先登录账号" else response.message
                        ?: "获取失败"
                    Resource.Error(errorMsg)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Resource.Error("网络异常: ${e.message}")
            }
    }

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

data class VideoAnalysisResult(
    val detail: VideoDetail,
    val videoFormats: List<FormatOption>,
    val audioFormats: List<FormatOption>,
    val durationSeconds: Long
)