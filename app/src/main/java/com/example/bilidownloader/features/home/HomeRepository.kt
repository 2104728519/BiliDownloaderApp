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
 *
 * 职责：
 * 1. 视频解析：处理链接、获取详情、计算 WBI、筛选 DASH 流。
 * 2. 历史记录写入。
 * 3. 获取账号云端历史记录。
 */
class HomeRepository(
    private val context: Context,
    private val historyDao: HistoryDao
) {
    private val apiService = NetworkModule.biliService
    // 专门处理重定向的轻量级 Client (用于 b23.tv 短链)
    private val redirectClient = OkHttpClient.Builder().followRedirects(true).build()

    // region 1. Video Analysis (视频解析业务)

    /**
     * 解析视频链接，获取可下载/播放的流媒体信息.
     */
    suspend fun analyzeVideo(input: String): Resource<VideoAnalysisResult> = withContext(Dispatchers.IO) {
        try {
            // --- 1. 链接提取与短链还原 ---
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

            // --- 2. 获取基础元数据 (View 接口) ---
            val viewResp = apiService.getVideoView(bvid).execute()
            val detail = viewResp.body()?.data ?: return@withContext Resource.Error("无法获取视频信息")

            // 默认取第一P
            val firstPage =
                detail.pages.firstOrNull() ?: return@withContext Resource.Error("该视频没有分P信息")

            // --- 3. 写入本地历史记录 ---
            historyDao.insertHistory(
                HistoryEntity(
                    bvid = detail.bvid,
                    title = detail.title,
                    coverUrl = detail.pic,
                    uploader = detail.owner.name,
                    timestamp = System.currentTimeMillis()
                )
            )

            // --- 4. 获取流地址 ---
            val formatsResult = fetchVideoFormats(detail.bvid, firstPage.cid)
            if (formatsResult is Resource.Error) {
                return@withContext Resource.Error(formatsResult.message ?: "获取流地址失败")
            }

            val formats = formatsResult.data!!

            Resource.Success(
                VideoAnalysisResult(
                    detail = detail,
                    videoFormats = formats.first,
                    audioFormats = formats.second
                )
            )

        } catch (e: Exception) {
            e.printStackTrace()
            Resource.Error("解析错误: ${e.message}")
        }
    }

    /**
     * 根据 bvid 和 cid 获取可用的视频和音频格式列表.
     */
    suspend fun fetchVideoFormats(
        bvid: String,
        cid: Long
    ): Resource<Pair<List<FormatOption>, List<FormatOption>>> = withContext(Dispatchers.IO) {
        try {
            // --- 1. WBI 签名 ---
            val navResp = apiService.getNavInfo().execute()
            val navData = navResp.body()?.data ?: return@withContext Resource.Error("无法获取密钥")
            val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
            val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

            // --- 2. 构建参数并请求流地址 ---
            val params = TreeMap<String, Any>().apply {
                put("bvid", bvid)
                put("cid", cid)
                put("qn", "127") // 请求 8K/4K 最高画质
                put("fnval", "4048") // 强制开启 DASH 格式
                put("fourk", "1")
            }
            val signedQuery = BiliSigner.signParams(params, mixinKey)

            val queryMap = signedQuery.split("&").associate {
                val p = it.split("=")
                URLDecoder.decode(p[0], "UTF-8") to URLDecoder.decode(p[1], "UTF-8")
            }

            val playResp = apiService.getPlayUrl(queryMap).execute()
            val playData = playResp.body()?.data ?: return@withContext Resource.Error("无法获取播放列表: ${playResp.errorBody()?.string()}")

            // --- 3. 格式筛选与封装 ---
            val videoOpts = mutableListOf<FormatOption>()
            val audioOpts = mutableListOf<FormatOption>()

            if (playData.dash != null) {
                val durationInSeconds = if (playData.timelength != null && playData.timelength > 0) {
                    playData.timelength / 1000L
                } else {
                    180L // 默认 3分钟
                }

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

            Resource.Success(Pair(finalVideoOpts, finalAudioOpts))
        } catch (e: Exception) {
            e.printStackTrace()
            Resource.Error("获取流地址失败: ${e.message}")
        }
    }

    // endregion

    suspend fun reportProgress(aid: Long, cid: Long) {
        try {
            val csrf = CookieManager.getCookieValue(context, "bili_jct") ?: return
            apiService.reportHistory(aid, cid, progress = 10, csrf = csrf)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // region 3. Cloud History (云端历史记录)

    /**
     * 获取 B 站账号云端的视频播放历史.
     * @param cursor 分页游标，为 null 时表示从第一页开始获取.
     * @return 返回一个包含历史列表和下一个分页游标的 Pair.
     */
    suspend fun fetchCloudHistory(cursor: HistoryCursor?): Resource<Pair<List<CloudHistoryItem>, HistoryCursor?>> =
        withContext(Dispatchers.IO) {
            try {
                // 1. API 调用
                val response = apiService.getHistory(
                    viewAt = cursor?.view_at ?: 0,
                    max = cursor?.max ?: 0
                )

                // 2. 结果处理
                if (response.code == 0) {
                    val data = response.data
                    val list = data?.list ?: emptyList()
                    val nextCursor = data?.cursor

                    // B站逻辑：如果返回的 list 为空，或 cursor.max 为 0，则表示没有更多数据
                    val hasMore = !list.isNullOrEmpty() && (nextCursor?.max ?: 0) > 0

                    Resource.Success(Pair(list, if (hasMore) nextCursor else null))
                } else {
                    // 特别处理未登录的情况
                    val errorMsg = if (response.code == -101) {
                        "请先登录账号"
                    } else {
                        response.message ?: "获取失败 (code: ${response.code})"
                    }
                    Resource.Error(errorMsg)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Resource.Error("网络异常: ${e.message}")
        }
    }

    // endregion

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

/**
 * 视频解析结果数据类.
 */
data class VideoAnalysisResult(
    val detail: VideoDetail,
    val videoFormats: List<FormatOption>,
    val audioFormats: List<FormatOption>
)