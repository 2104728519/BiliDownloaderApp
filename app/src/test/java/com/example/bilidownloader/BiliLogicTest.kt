package com.example.bilidownloader

import com.example.bilidownloader.data.api.RetrofitClient
import com.example.bilidownloader.utils.BiliSigner
import org.junit.Test
import java.util.TreeMap

/**
 * 这是一个“演习场”
 * 我们在这里模拟 APP 运行的核心流程
 */
class BiliLogicTest {

    @Test
    fun testGetVideoUrl() {
        println("========== 测试开始 ==========")

        // 0. 准备一个真实的 BV 号
        val bvid = "BV15LkXBGE5r"
        println("目标视频: $bvid")

        // ---------------------------------------------------------
        // 第一步：获取 WBI 密钥
        // ---------------------------------------------------------
        val navResponse = RetrofitClient.service.getNavInfo().execute()
        val navData = navResponse.body()?.data

        if (navData == null) {
            println("❌ 第一步失败：没拿到导航数据")
            return
        }

        val imgUrl = navData.wbi_img.img_url
        val subUrl = navData.wbi_img.sub_url
        val imgKey = imgUrl.substringAfterLast("/").substringBefore(".")
        val subKey = subUrl.substringAfterLast("/").substringBefore(".")

        println("✅ 第一步成功：拿到原始密钥")
        println("   imgKey: $imgKey")
        println("   subKey: $subKey")

        // ---------------------------------------------------------
        // 第二步：计算混合密钥
        // ---------------------------------------------------------
        val mixinKey = BiliSigner.getMixinKey(imgKey, subKey)
        println("✅ 第二步成功：计算出混合密钥 -> $mixinKey")

        // ---------------------------------------------------------
        // 第三步：获取视频 CID (【修改】这里升级了！)
        // ---------------------------------------------------------
        // 旧代码：getPageList(bvid) -> 已经删了
        // 新代码：getVideoView(bvid) -> 使用新接口
        val viewResponse = RetrofitClient.service.getVideoView(bvid).execute()
        val videoDetail = viewResponse.body()?.data

        if (videoDetail == null) {
            println("❌ 第三步失败：没拿到视频详情")
            return
        }

        // 新接口的数据结构变了，CID 藏在 pages 列表里
        val cid = videoDetail.pages[0].cid
        println("✅ 第三步成功：拿到视频 CID -> $cid")
        println("   视频标题: ${videoDetail.title}") // 顺便打印一下标题

        // ---------------------------------------------------------
        // 第四步：签名并请求播放地址
        // ---------------------------------------------------------
        val params = TreeMap<String, Any>()
        params["bvid"] = bvid
        params["cid"] = cid
        params["qn"] = "80"
        params["fnval"] = "4048"
        params["fourk"] = "1"

        val signedQuery = BiliSigner.signParams(params, mixinKey)
        println("📝 参数签名结果: $signedQuery")

        val queryMap = mutableMapOf<String, String>()
        signedQuery.split("&").forEach { pair ->
            val parts = pair.split("=")
            if (parts.size == 2) {
                val key = java.net.URLDecoder.decode(parts[0], "UTF-8")
                val value = java.net.URLDecoder.decode(parts[1], "UTF-8")
                queryMap[key] = value
            }
        }

        val playResponse = RetrofitClient.service.getPlayUrl(queryMap).execute()
        val playData = playResponse.body()?.data

        // ---------------------------------------------------------
        // 安全检查
        // ---------------------------------------------------------
        val dashData = playData?.dash

        if (dashData == null) {
            println("❌ 第四步失败：没拿到播放地址")
            println("   API 返回: ${playResponse.body()}")
            return
        }

        // ---------------------------------------------------------
        // 第五步：展示战利品
        // ---------------------------------------------------------
        val videoList = dashData.video
        val audioList = dashData.audio

        println("\n🎉🎉🎉 测试通关！成功拿到下载地址！🎉🎉🎉")

        if (videoList.isNotEmpty()) {
            println("🎥 视频流 (Video):")
            println("   画质ID: ${videoList[0].id}")
            println("   地址: ${videoList[0].baseUrl.substring(0, 50)}...")
        }

        if (!audioList.isNullOrEmpty()) {
            println("🎵 音频流 (Audio):")
            println("   地址: ${audioList[0].baseUrl.substring(0, 50)}...")
        }

        println("========== 测试结束 ==========")
    }
}