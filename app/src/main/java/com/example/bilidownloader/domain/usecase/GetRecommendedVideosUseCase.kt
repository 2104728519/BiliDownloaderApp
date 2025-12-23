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

/**
 * 推荐视频获取用例 (自动化数据源).
 *
 * 负责获取首页推荐流，并进行 **本地去重**。
 * 避免自动化脚本对同一个视频重复执行“总结-评论”流程，节省 Token 和 API 调用。
 */
class GetRecommendedVideosUseCase(
    private val recommendRepository: RecommendRepository,
    private val subtitleRepository: SubtitleRepository,
    private val apiService: BiliApiService
) {

    suspend operator fun invoke(): Resource<List<CandidateVideo>> = withContext(Dispatchers.IO) {
        try {
            // 1. 获取 WBI 签名所需参数 (首页推荐接口也开启了 WBI 校验)
            val navResponse = apiService.getNavInfo().execute()
            val navData = navResponse.body()?.data ?: return@withContext Resource.Error("无法获取 WBI 密钥")
            val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
            val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

            // 2. 请求推荐流
            val params = TreeMap<String, Any>()
            params["ps"] = 12 // 请求数量
            val signedQuery = BiliSigner.signParams(params, mixinKey)
            val wts = params["wts"] as Long
            val wRid = signedQuery.substringAfter("w_rid=")

            val recommendResult = recommendRepository.fetchRawRecommend(wts, wRid)
            if (recommendResult is Resource.Error) {
                return@withContext Resource.Error(recommendResult.message!!)
            }

            // 3. 数据处理与去重
            val rawList = recommendResult.data ?: emptyList()
            val validCandidates = mutableListOf<CandidateVideo>()

            for (item in rawList) {
                // A. 本地数据库布隆过滤：检查是否已评论过
                if (recommendRepository.isVideoProcessed(item.bvid)) continue

                // B. 补充 CID 信息 (推荐流只返回了基础信息，无 CID 无法获取字幕)
                try {
                    val viewResp = apiService.getVideoView(item.bvid).execute()
                    val videoDetail = viewResp.body()?.data
                    val cid = videoDetail?.pages?.firstOrNull()?.cid

                    if (cid != null) {
                        // 构造候选对象 (SubtitleData 留空，后续步骤再延迟加载)
                        validCandidates.add(CandidateVideo(item, null, cid))
                    }
                } catch (e: Exception) {
                    continue // 单个视频解析失败不影响整体
                }
            }

            if (validCandidates.isEmpty()) {
                return@withContext Resource.Error("没有获取到新的推荐视频 (均已处理过)")
            }

            Resource.Success(validCandidates)

        } catch (e: Exception) {
            e.printStackTrace()
            Resource.Error("推荐流程异常: ${e.message}")
        }
    }
}