package com.example.bilidownloader.domain.usecase

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.data.api.BiliApiService
import com.example.bilidownloader.data.model.ConclusionData
import com.example.bilidownloader.data.repository.SubtitleRepository
import com.example.bilidownloader.core.util.BiliSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TreeMap

/**
 * 业务逻辑：获取 AI 字幕
 * 职责：
 * 1. 获取 WBI 密钥 (Nav接口)
 * 2. 计算 WBI 签名 (w_rid)
 * 3. 请求字幕数据
 */
class GetSubtitleUseCase(
    private val subtitleRepository: SubtitleRepository,
    private val apiService: BiliApiService
) {
    suspend operator fun invoke(
        bvid: String,
        cid: Long,
        upMid: Long?
    ): Resource<ConclusionData> = withContext(Dispatchers.IO) {
        try {
            // 1. 获取 Nav 信息以拿到 WBI 密钥 (img_key, sub_key)
            // 因为 apiService.getNavInfo() 返回的是 Call，我们用 execute() 同步请求
            val navResponse = apiService.getNavInfo().execute()

            val navData = navResponse.body()?.data
            if (navData == null) {
                return@withContext Resource.Error("无法获取 WBI 密钥，请检查网络或登录状态")
            }

            // 2. 计算 Mixin Key (混合密钥)
            val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
            val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

            // 3. 准备参数并签名
            // 使用 TreeMap 是为了保证参数有序，这是签名的要求
            val params = TreeMap<String, Any>()
            params["bvid"] = bvid
            params["cid"] = cid
            if (upMid != null) {
                params["up_mid"] = upMid
            }

            // signParams 会在 params 中自动插入 "wts" (时间戳)
            // 返回值是形如 "bvid=xxx&cid=xxx&wts=123...&w_rid=xxx" 的字符串
            val signedQueryString = BiliSigner.signParams(params, mixinKey)

            // 4. 从结果中提取 wts 和 w_rid
            // 因为我们的 Retrofit 接口定义的参数是独立的，所以需要提取出来
            val wts = params["wts"] as Long
            val wRid = signedQueryString.substringAfter("w_rid=")

            // 5. 调用 Repository 获取字幕
            return@withContext subtitleRepository.getSubtitle(
                bvid = bvid,
                cid = cid,
                upMid = upMid,
                wts = wts,
                wRid = wRid
            )

        } catch (e: Exception) {
            e.printStackTrace()
            Resource.Error("字幕解析流程异常: ${e.message}")
        }
    }
}