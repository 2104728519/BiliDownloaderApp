package com.example.bilidownloader.data.repository

import android.util.Log
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.data.api.BiliApiService
import com.example.bilidownloader.data.model.*
import com.example.bilidownloader.core.util.BiliSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TreeMap

class SubtitleRepository(
    private val apiService: BiliApiService
) {
    suspend fun getSubtitle(
        bvid: String,
        cid: Long,
        upMid: Long?, // 此时 upMid 可能没用上，但保持签名兼容
        wts: Long,    // 注意：这是外面传进来的旧时间戳，Plan B 需要重新生成
        wRid: String  // 这是旧签名，Plan B 需要重新生成
    ): Resource<ConclusionData> = withContext(Dispatchers.IO) {

        // --- Plan A: 尝试获取 AI 总结 (原有逻辑) ---
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

        // --- Plan B: 播放器字幕兜底 (新逻辑) ---
        return@withContext fetchPlayerSubtitle(bvid, cid)
    }

    private suspend fun fetchPlayerSubtitle(bvid: String, cid: Long): Resource<ConclusionData> {
        try {
            // 1. 获取 Aid (PlayerV2 需要 aid，不是 bvid)
            // 我们可以简单地通过 bvid 获取 aid，或者如果你在 ViewModel 里存了 aid 最好直接传
            // 这里为了通用，快速查一下 View 接口
            val viewResp = apiService.getVideoView(bvid).execute()
            val aid = viewResp.body()?.data?.aid ?: return Resource.Error("无法解析 AID")

            // 2. 重新计算 WBI 签名 (因为参数变了：只有 aid 和 cid)
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

            // 3. 请求播放器配置
            val playerResp = apiService.getPlayerV2(aid, cid, newWts, newWRid)
            val subtitles = playerResp.data?.subtitle?.subtitles

            if (subtitles.isNullOrEmpty()) {
                return Resource.Error("该视频完全没有字幕 (CC或AI都没有)")
            }

            // 4. 挑选最佳字幕 (优先中文)
            // 这里的 subtitle_url 通常以 "//" 开头，需要补全 "https:"
            val targetSub = subtitles.find { it.lanDoc.contains("中") } ?: subtitles.first()
            var subUrl = targetSub.subtitleUrl
            if (subUrl.startsWith("//")) subUrl = "https:$subUrl"

            // 5. 下载字幕 JSON
            val rawJson = apiService.downloadSubtitleJson(subUrl)

            // 6. 转换为 ConclusionData 格式 (适配现有 ViewModel)
            val convertedData = convertToConclusionData(rawJson, targetSub.lanDoc)

            return Resource.Success(convertedData)

        } catch (e: Exception) {
            e.printStackTrace()
            return Resource.Error("Plan B 失败: ${e.message}")
        }
    }

    // 适配器：把字幕 JSON 包装成 AI 总结的格式
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