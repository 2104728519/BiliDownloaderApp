package com.example.bilidownloader.data.repository

import android.content.Context
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.manager.CookieManager
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.core.database.CommentedVideoDao
import com.example.bilidownloader.core.database.CommentedVideoEntity
import com.example.bilidownloader.core.model.CandidateVideo
import com.example.bilidownloader.core.model.RecommendItem
import com.example.bilidownloader.core.util.BiliSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TreeMap

/**
 * 推荐流仓库.
 * 负责获取 B 站首页推荐视频，并维护“已处理视频”的本地状态。
 */
class RecommendRepository(
    private val context: Context,
    private val commentedVideoDao: CommentedVideoDao
) {
    private val apiService = NetworkModule.biliService

    /**
     * 获取候选视频列表 (原 GetRecommendedVideosUseCase 逻辑).
     * 包含：WBI签名 -> 获取推荐 -> 本地去重 -> 获取CID.
     */
    suspend fun fetchCandidateVideos(): Resource<List<CandidateVideo>> = withContext(Dispatchers.IO) {
        try {
            // 1. 获取 WBI 签名
            val navResponse = apiService.getNavInfo().execute()
            val navData = navResponse.body()?.data ?: return@withContext Resource.Error("无法获取 WBI 密钥")
            val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
            val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

            // 2. 请求推荐流
            val params = TreeMap<String, Any>()
            params["ps"] = 12
            val signedQuery = BiliSigner.signParams(params, mixinKey)
            val wts = params["wts"] as Long
            val wRid = signedQuery.substringAfter("w_rid=")

            val response = apiService.getRecommendFeed(wts, wRid)
            if (response.code != 0 || response.data?.item == null) {
                return@withContext Resource.Error(response.message ?: "获取推荐失败")
            }

            // 3. 数据处理与去重
            val rawList = response.data.item
            val validCandidates = mutableListOf<CandidateVideo>()

            for (item in rawList) {
                // A. 本地数据库布隆过滤
                if (commentedVideoDao.isProcessed(item.bvid)) continue

                // B. 补充 CID 信息
                try {
                    val viewResp = apiService.getVideoView(item.bvid).execute()
                    val videoDetail = viewResp.body()?.data
                    val cid = videoDetail?.pages?.firstOrNull()?.cid

                    if (cid != null) {
                        validCandidates.add(CandidateVideo(item, null, cid))
                    }
                } catch (e: Exception) {
                    continue
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

    suspend fun markVideoAsProcessed(item: RecommendItem) {
        val entity = CommentedVideoEntity(
            bvid = item.bvid,
            oid = item.id,
            title = item.title
        )
        commentedVideoDao.insert(entity)
    }

    suspend fun reportProgress(aid: Long, cid: Long) {
        try {
            val csrf = CookieManager.getCookieValue(context, "bili_jct") ?: return
            apiService.reportHistory(aid, cid, progress = 10, csrf = csrf)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}