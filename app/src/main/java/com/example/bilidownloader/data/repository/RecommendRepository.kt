package com.example.bilidownloader.data.repository

import android.content.Context
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.manager.CookieManager
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.core.database.CommentedVideoDao
import com.example.bilidownloader.core.database.CommentedVideoEntity
import com.example.bilidownloader.core.model.RecommendItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 推荐流仓库.
 *
 * 负责获取 B 站首页推荐视频，并维护“已处理视频”的本地状态。
 * 同时负责上报观看进度，以“欺骗” B 站推荐算法，使其推送更多相关视频。
 */
class RecommendRepository(
    private val context: Context,
    private val commentedVideoDao: CommentedVideoDao
) {
    private val apiService = NetworkModule.biliService

    suspend fun fetchRawRecommend(wts: Long, wRid: String): Resource<List<RecommendItem>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getRecommendFeed(wts, wRid)
            if (response.code == 0 && response.data?.item != null) {
                Resource.Success(response.data.item)
            } else {
                Resource.Error(response.message ?: "获取推荐失败 (code: ${response.code})")
            }
        } catch (e: Exception) {
            Resource.Error("网络异常: ${e.message}")
        }
    }

    suspend fun isVideoProcessed(bvid: String): Boolean {
        return commentedVideoDao.isProcessed(bvid)
    }

    suspend fun markVideoAsProcessed(item: RecommendItem) {
        val entity = CommentedVideoEntity(
            bvid = item.bvid,
            oid = item.id,
            title = item.title
        )
        commentedVideoDao.insert(entity)
    }

    /**
     * 上报模拟观看进度 (10秒).
     * 用于增加账号活跃度权重.
     */
    suspend fun reportProgress(aid: Long, cid: Long) {
        try {
            val csrf = CookieManager.getCookieValue(context, "bili_jct") ?: return
            apiService.reportHistory(aid, cid, progress = 10, csrf = csrf)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}