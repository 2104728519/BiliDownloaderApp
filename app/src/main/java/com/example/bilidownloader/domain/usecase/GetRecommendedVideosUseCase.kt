package com.example.bilidownloader.domain.usecase

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.data.api.BiliApiService
import com.example.bilidownloader.data.model.CandidateVideo
import com.example.bilidownloader.data.repository.RecommendRepository
import com.example.bilidownloader.data.repository.SubtitleRepository
import com.example.bilidownloader.core.util.BiliSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TreeMap

class GetRecommendedVideosUseCase(
    private val recommendRepository: RecommendRepository,
    private val subtitleRepository: SubtitleRepository,
    private val apiService: BiliApiService
) {

    suspend operator fun invoke(): Resource<List<CandidateVideo>> = withContext(Dispatchers.IO) {
        try {
            // 1. 获取密钥
            val navResponse = apiService.getNavInfo().execute()
            val navData = navResponse.body()?.data ?: return@withContext Resource.Error("无法获取 WBI 密钥")
            val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
            val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

            // 2. 获取推荐列表
            val params = TreeMap<String, Any>()
            params["ps"] = 12 // 请求 12 条
            val signedQuery = BiliSigner.signParams(params, mixinKey)
            val wts = params["wts"] as Long
            val wRid = signedQuery.substringAfter("w_rid=")

            val recommendResult = recommendRepository.fetchRawRecommend(wts, wRid)
            if (recommendResult is Resource.Error) {
                return@withContext Resource.Error(recommendResult.message!!)
            }

            // [关键修改] 不再并发请求字幕，只做本地去重
            val rawList = recommendResult.data ?: emptyList()
            val validCandidates = mutableListOf<CandidateVideo>()

            for (item in rawList) {
                // A. 本地数据库去重
                if (recommendRepository.isVideoProcessed(item.bvid)) continue

                // B. 获取 CID (这一步还是需要的，因为没有 CID 没法查字幕)
                // 注意：获取 VideoView 接口相对宽松，但也建议稍微 catch 一下异常
                try {
                    val viewResp = apiService.getVideoView(item.bvid).execute()
                    val videoDetail = viewResp.body()?.data
                    val cid = videoDetail?.pages?.firstOrNull()?.cid

                    if (cid != null) {
                        // [重点] subtitleData 传 null，稍后再查
                        validCandidates.add(CandidateVideo(item, null, cid))
                    }
                } catch (e: Exception) {
                    continue // 单个失败跳过
                }
            }

            if (validCandidates.isEmpty()) {
                return@withContext Resource.Error("没有获取到新的推荐视频")
            }

            Resource.Success(validCandidates)

        } catch (e: Exception) {
            e.printStackTrace()
            Resource.Error("推荐流程异常: ${e.message}")
        }
    }
}