package com.example.bilidownloader.data.repository

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.data.api.BiliApiService
import com.example.bilidownloader.data.model.ConclusionData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

class SubtitleRepository(private val apiService: BiliApiService) {

    suspend fun getSubtitle(
        bvid: String,
        cid: Long,
        upMid: Long?,
        wts: Long,
        wRid: String
    ): Resource<ConclusionData> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getConclusion(bvid, cid, upMid, wts, wRid)

            // 1. 基础 HTTP 成功检查
            if (response.code == 0 && response.data != null) {
                val result = response.data.modelResult

                // 2. 【核心修复】检查是否有真正的内容
                // 如果 summary 是空的，并且 subtitle 列表也是空的，说明 B 站没有生成字幕
                val hasSummary = !result?.summary.isNullOrEmpty()
                val hasSubtitle = !result?.subtitle.isNullOrEmpty()

                if (result != null && (hasSummary || hasSubtitle)) {
                    Resource.Success(response.data)
                } else {
                    // 虽然请求通了，但内容为空，视为业务层面的“未找到”
                    Resource.Error("该视频暂无 AI 字幕或摘要生成")
                }
            } else {
                val msg = response.message ?: "获取字幕失败 (Code: ${response.code})"
                Resource.Error(msg)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            val errorMsg = if (e is IOException) {
                "网络连接失败，请检查网络"
            } else {
                e.message ?: "获取字幕时发生未知错误"
            }
            Resource.Error(errorMsg)
        }
    }
}