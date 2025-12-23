package com.example.bilidownloader.domain.usecase

import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.network.api.BiliApiService
import com.example.bilidownloader.core.model.ConclusionData
import com.example.bilidownloader.data.repository.SubtitleRepository
import com.example.bilidownloader.core.util.BiliSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.TreeMap

/**
 * 字幕获取用例.
 *
 * 封装了字幕请求前的 WBI 签名逻辑。
 * B 站的 AI 摘要接口 (`conclusion/get`) 对签名校验极为严格，必须在此处完成完整的签名计算。
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
            // 1. 同步获取 WBI 密钥
            val navResponse = apiService.getNavInfo().execute()
            val navData = navResponse.body()?.data
            if (navData == null) {
                return@withContext Resource.Error("无法获取 WBI 密钥，请检查网络或登录状态")
            }

            val imgKey = navData.wbi_img.img_url.substringAfterLast("/").substringBefore(".")
            val subKey = navData.wbi_img.sub_url.substringAfterLast("/").substringBefore(".")
            val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)

            // 2. 参数签名
            // up_mid 虽为可选参数，但 B 站风控建议带上
            val params = TreeMap<String, Any>()
            params["bvid"] = bvid
            params["cid"] = cid
            if (upMid != null) {
                params["up_mid"] = upMid
            }

            // 签名过程会自动注入 'wts' (时间戳)
            val signedQueryString = BiliSigner.signParams(params, mixinKey)

            val wts = params["wts"] as Long
            val wRid = signedQueryString.substringAfter("w_rid=")

            // 3. 委托 Repository 执行实际请求 (含双重兜底逻辑)
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