package com.example.bilidownloader.data.repository

import android.util.Log
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.model.ConclusionData
import com.example.bilidownloader.core.model.ModelResult
import com.example.bilidownloader.core.model.PartSubtitleItem
import com.example.bilidownloader.core.model.RawSubtitleJson
import com.example.bilidownloader.core.model.SubtitleContainer
import com.example.bilidownloader.core.network.api.BiliApiService
import com.example.bilidownloader.core.util.BiliSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TreeMap

/**
 * 字幕/摘要仓库.
 *
 * 实现了核心的“双重兜底策略”：
 * 1. **Plan A (AI 摘要)**: 优先尝试获取 B 站生成的 AI 总结和 AI 增强字幕。
 * 2. **Plan B (播放器 CC 字幕)**: 若 Plan A 失败（如视频无 AI 摘要），自动降级为解析播放器接口，
 *    获取传统的 CC 字幕文件，并将其转换为统一的数据格式。
 */
class SubtitleRepository(
    private val apiService: BiliApiService
) {
    suspend fun getSubtitle(
        bvid: String,
        cid: Long,
        upMid: Long?,
        wts: Long,
        wRid: String
    ): Resource<ConclusionData> = withContext(Dispatchers.IO) {

        // --- Plan A: 尝试 AI 总结接口 ---
        try {
            val fakeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
            val fakeReferer = "https://www.bilibili.com/video/$bvid"

            val response = apiService.getConclusion(
                userAgent = fakeUserAgent,
                referer = fakeReferer,
                bvid = bvid,
                cid = cid,
                upMid = upMid,
                wts = wts,
                wRid = wRid
            )

            if (response.code == 0) {
                val result = response.data?.modelResult
                if (result != null && (!result.summary.isNullOrEmpty() || !result.subtitle.isNullOrEmpty())) {
                    return@withContext Resource.Success(response.data)
                }
            }
            Log.w("SubtitleRepo", "Plan A (AI总结) 失败或为空，尝试 Plan B (播放器字幕)...")
        } catch (e: Exception) {
            Log.w("SubtitleRepo", "Plan A 异常: ${e.message}")
        }

        // --- Plan B: 播放器字幕兜底 ---
        return@withContext fetchPlayerSubtitle(bvid, cid)
    }

    /**
     * 执行 Plan B: 从播放器配置 V2 接口提取 CC 字幕.
     */
    private suspend fun fetchPlayerSubtitle(bvid: String, cid: Long): Resource<ConclusionData> {
        try {
            // 1. 获取 Aid (PlayerV2 接口只接受 aid)
            val viewResp = apiService.getVideoView(bvid).execute()
            val aid = viewResp.body()?.data?.aid ?: return Resource.Error("无法解析 AID")

            // 2. 重新计算 WBI 签名 (参数集变更为 aid + cid)
            val navResponse = apiService.getNavInfo().execute()
            val navData = navResponse.body()?.data ?: return Resource.Error("密钥获取失败")
            val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
            val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

            val params = TreeMap<String, Any>()
            params["aid"] = aid
            params["cid"] = cid
            val signedQuery = BiliSigner.signParams(params, mixinKey)
            val newWts = params["wts"] as Long
            val newWRid = signedQuery.substringAfter("w_rid=")

            // 3. 请求播放器接口
            val playerResp = apiService.getPlayerV2(aid, cid, newWts, newWRid)
            val subtitles = playerResp.data?.subtitle?.subtitles

            if (subtitles.isNullOrEmpty()) {
                return Resource.Error("该视频完全没有字幕 (CC或AI都没有)")
            }

            // 4. 挑选最佳字幕 (优先中文)
            val targetSub = subtitles.find { it.lanDoc.contains("中") } ?: subtitles.first()
            var subUrl = targetSub.subtitleUrl
            if (subUrl.startsWith("//")) subUrl = "https:$subUrl"

            // 5. 下载字幕 JSON
            val rawJson = apiService.downloadSubtitleJson(subUrl)

            // 6. 数据适配 (RawJson -> ConclusionData)
            val convertedData = convertToConclusionData(rawJson, targetSub.lanDoc)

            return Resource.Success(convertedData)

        } catch (e: Exception) {
            e.printStackTrace()
            return Resource.Error("Plan B 失败: ${e.message}")
        }
    }

    private fun convertToConclusionData(raw: RawSubtitleJson, lanDoc: String): ConclusionData {
        val partSubtitles = raw.body?.map {
            PartSubtitleItem(
                startTimestamp = it.from.toLong(),
                endTimestamp = it.to.toLong(),
                content = it.content
            )
        } ?: emptyList()

        return ConclusionData(
            stid = "",
            modelResult = ModelResult(
                summary = "（此内容来自播放器字幕 [$lanDoc]，无 AI 摘要）",
                outline = null,
                subtitle = listOf(
                    SubtitleContainer(
                        partSubtitle = partSubtitles,
                        lan = lanDoc
                    )
                )
            )
        )
    }
}