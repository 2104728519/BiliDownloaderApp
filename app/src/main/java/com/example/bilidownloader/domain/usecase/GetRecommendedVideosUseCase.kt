package com.example.bilidownloader.domain.usecase

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.data.api.BiliApiService
import com.example.bilidownloader.data.model.CandidateVideo
import com.example.bilidownloader.data.repository.RecommendRepository
import com.example.bilidownloader.data.repository.SubtitleRepository
import com.example.bilidownloader.utils.BiliSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import java.util.TreeMap
import com.example.bilidownloader.data.model.RecommendItem
class GetRecommendedVideosUseCase(
    private val recommendRepository: RecommendRepository,
    private val subtitleRepository: SubtitleRepository,
    private val apiService: BiliApiService // 用于获取 WBI 密钥
) {

    suspend operator fun invoke(): Resource<List<CandidateVideo>> = withContext(Dispatchers.IO) {
        try {
            // 1. 获取 WBI 密钥 (这一步和 GetSubtitleUseCase 类似，但为了解耦我们重写一次或封装工具，这里直接写)
            val navResponse = apiService.getNavInfo().execute()
            val navData = navResponse.body()?.data ?: return@withContext Resource.Error("无法获取 WBI 密钥")

            val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
            val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

            // 2. 准备推荐接口的签名
            val params = TreeMap<String, Any>()
            // 推荐接口只要 ps 参数，wts 由 signParams 自动添加
            params["ps"] = 14 // 请求 14 条，预留过滤空间
            val signedQuery = BiliSigner.signParams(params, mixinKey)
            val wts = params["wts"] as Long
            val wRid = signedQuery.substringAfter("w_rid=")

            // 3. 获取推荐列表
            val recommendResult = recommendRepository.fetchRawRecommend(wts, wRid)
            if (recommendResult is Resource.Error) {
                return@withContext Resource.Error(recommendResult.message!!)
            }
            val rawList = recommendResult.data ?: emptyList()

            // 4. 并发处理：过滤 + 获取字幕
            // 我们使用 async/awaitAll 来同时请求多个视频的字幕，提高速度
            val validCandidates = rawList.map { item ->
                async {
                    processSingleVideo(item, mixinKey)
                }
            }.awaitAll().filterNotNull() // 去掉 null (即无字幕或已处理的视频)

            if (validCandidates.isEmpty()) {
                return@withContext Resource.Error("当前推荐流中暂无带字幕的视频，请稍后重试")
            }

            Resource.Success(validCandidates)

        } catch (e: Exception) {
            e.printStackTrace()
            Resource.Error("推荐获取流程异常: ${e.message}")
        }
    }

    /**
     * 处理单个视频：
     * 1. 检查数据库是否已处理
     * 2. 解析 CID
     * 3. 尝试获取字幕
     * 成功返回 CandidateVideo，失败/被过滤返回 null
     */
    private suspend fun processSingleVideo(item: RecommendItem, mixinKey: String): CandidateVideo? {
        try {
            // A. 本地去重过滤
            if (recommendRepository.isVideoProcessed(item.bvid)) {
                return null
            }

            // B. 获取视频详情以拿到 CID (推荐接口不直接给 CID，只给 BVID)
            // 这一步虽然有点耗时，但必须拿到 CID 才能查字幕
            // 为了优化，我们可以只请求必要的字段，但目前复用现有接口比较稳妥
            val viewResp = apiService.getVideoView(item.bvid).execute()
            val videoDetail = viewResp.body()?.data ?: return null
            val cid = videoDetail.pages.firstOrNull()?.cid ?: return null

            // C. 尝试获取字幕 (复用 SubtitleRepository)
            // 重新签名，因为 bvid 和 cid 变了
            val subParams = TreeMap<String, Any>().apply {
                put("bvid", item.bvid)
                put("cid", cid)
                put("up_mid", item.owner?.mid ?: 0)
            }
            val subSignedStr = BiliSigner.signParams(subParams, mixinKey)
            val subWts = subParams["wts"] as Long
            val subWRid = subSignedStr.substringAfter("w_rid=")

            val subResult = subtitleRepository.getSubtitle(
                bvid = item.bvid,
                cid = cid,
                upMid = item.owner?.mid,
                wts = subWts,
                wRid = subWRid
            )

            // D. 只有成功获取到字幕才保留
            if (subResult is Resource.Success && subResult.data != null) {
                val data = subResult.data
                // 再次检查内容是否为空
                val hasContent = !data.modelResult?.summary.isNullOrEmpty() ||
                        !data.modelResult?.subtitle.isNullOrEmpty()

                if (hasContent) {
                    return CandidateVideo(item, data, cid)
                }
            }
            return null
        } catch (e: Exception) {
            return null // 单个视频失败不影响整体
        }
    }
}