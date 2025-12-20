package com.example.bilidownloader.data.repository

import android.content.Context
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.manager.CookieManager
import com.example.bilidownloader.core.network.NetworkModule
import com.example.bilidownloader.data.database.CommentedVideoDao
import com.example.bilidownloader.data.database.CommentedVideoEntity
import com.example.bilidownloader.data.model.RecommendItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class RecommendRepository(
    private val context: Context,
    private val commentedVideoDao: CommentedVideoDao
) {
    private val apiService = NetworkModule.biliService

    // 获取原始推荐流 (不含字幕检查)
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

    // 检查本地数据库是否已处理过
    suspend fun isVideoProcessed(bvid: String): Boolean {
        return commentedVideoDao.isProcessed(bvid)
    }

    // 标记视频为已处理
    suspend fun markVideoAsProcessed(item: RecommendItem) {
        val entity = CommentedVideoEntity(
            bvid = item.bvid,
            oid = item.id,
            title = item.title
        )
        commentedVideoDao.insert(entity)
    }

    // 上报观看进度 (欺骗推荐算法)
    suspend fun reportProgress(aid: Long, cid: Long) {
        try {
            val csrf = CookieManager.getCookieValue(context, "bili_jct") ?: return
            // 上报进度为 10 秒，表示“稍微看了一点”
            apiService.reportHistory(aid, cid, progress = 10, csrf = csrf)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}