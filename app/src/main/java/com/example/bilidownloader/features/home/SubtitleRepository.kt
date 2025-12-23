package com.example.bilidownloader.features.home

import android.util.Log
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.network.api.BiliApiService
import com.example.bilidownloader.core.model.*
import com.example.bilidownloader.core.util.BiliSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TreeMap

class SubtitleRepository(
    private val apiService: BiliApiService
) {

    /**
     * 获取字幕 (包含 WBI 签名逻辑).
     * 原 GetSubtitleUseCase 逻辑已合并至此.
     */
    suspend fun getSubtitleWithSign(
        bvid: String,
        cid: Long,
        upMid: Long?
    ): Resource<ConclusionData> = withContext(Dispatchers.IO) {
        try {
            // 1. 同步获取 WBI 密钥
            val navResponse = apiService.getNavInfo().execute()
            val navData = navResponse.body()?.data
                ?: return@withContext Resource.Error("无法获取 WBI 密钥")

            val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
            val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

            // 2. 参数签名
            val params = TreeMap<String, Any>()
            params["bvid"] = bvid
            params["cid"] = cid
            if (upMid != null) params["up_mid"] = upMid

            val signedQueryString = BiliSigner.signParams(params, mixinKey)
            val wts = params["wts"] as Long
            val wRid = signedQueryString.substringAfter("w_rid=")

            // 3. 执行请求 (Plan A + Plan B)
            return@withContext getSubtitleInternal(bvid, cid, upMid, wts, wRid)

        } catch (e: Exception) {
            e.printStackTrace()
            Resource.Error("字幕解析异常: ${e.message}")
        }
    }

    private suspend fun getSubtitleInternal(
        bvid: String,
        cid: Long,
        upMid: Long?,
        wts: Long,
        wRid: String
    ): Resource<ConclusionData> {
        // --- Plan A: AI 总结 ---
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
                    return Resource.Success(response.data)
                }
            }
        } catch (e: Exception) {
            Log.w("SubtitleRepo", "Plan A 失败: ${e.message}")
        }

        // --- Plan B: 播放器字幕 ---
        return fetchPlayerSubtitle(bvid, cid)
    }

    // ... (fetchPlayerSubtitle 和 convertToConclusionData 方法保持不变，请保留原有的代码) ...
    // 为节省篇幅，这里省略 fetchPlayerSubtitle 的具体实现，请确保不要删除它
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

    private fun convertToConclusionData(raw: com.example.bilidownloader.core.model.RawSubtitleJson, lanDoc: String): ConclusionData {
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