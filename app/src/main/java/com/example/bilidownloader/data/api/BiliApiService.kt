package com.example.bilidownloader.data.api

import com.example.bilidownloader.data.model.*
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.QueryMap

/**
 * 这里是“点菜单”
 * 我们在这里列出所有需要向 B 站请求的功能
 */
interface BiliApiService {

    // 1. 获取导航信息 (为了拿到 WBI 签名用的密钥)
    // 就像打电话问前台：今天的加密暗号是什么？
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/web-interface/nav")
    fun getNavInfo(): Call<BiliResponse<NavData>>

    // 2. 【替换】获取视频详细信息 (标题、封面、CID等)
    // 这个接口比原来的 pagelist 提供了更多的视频信息。
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/web-interface/view")
    fun getVideoView(@Query("bvid") bvid: String): Call<BiliResponse<VideoDetail>>

    // 3. 获取视频播放地址 (最关键的一步)
    // 就像拿着刚才算好的加密纸条去仓库提货
    // @QueryMap 表示我们要把一大堆参数（cid, qn, w_rid...）一次性传进去
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/player/wbi/playurl")
    fun getPlayUrl(@QueryMap params: Map<String, String>): Call<BiliResponse<PlayData>>
}