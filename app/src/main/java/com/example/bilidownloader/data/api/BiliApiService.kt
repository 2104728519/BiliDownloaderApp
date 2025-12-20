package com.example.bilidownloader.data.api

import com.example.bilidownloader.data.model.*
import retrofit2.Call
import retrofit2.http.*

// =========================================================
// UserInfo 数据模型
// =========================================================

data class UserInfoResponse(
    val code: Int,
    val data: UserInfoData?
)

data class UserInfoData(
    val mid: Long,
    val uname: String,
    val face: String,
    val isLogin: Boolean
)

// =========================================================

/**
 * B站 API 接口定义
 */
interface BiliApiService {

    // 1. 获取导航信息 (用于 WBI 签名)
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/web-interface/nav")
    fun getNavInfo(): Call<BiliResponse<NavData>>

    // 获取当前登录用户的信息
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/web-interface/nav")
    fun getSelfInfo(): Call<UserInfoResponse>


    // 2. 获取视频详细信息
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/web-interface/view")
    fun getVideoView(@Query("bvid") bvid: String): Call<BiliResponse<VideoDetail>>

    // 3. 获取视频播放地址
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/player/wbi/playurl")
    fun getPlayUrl(@QueryMap params: Map<String, String>): Call<BiliResponse<PlayData>>

    /**
     * 获取视频 AI 摘要和字幕 (高风险接口，易被风控)
     */
    @GET("x/web-interface/view/conclusion/get")
    suspend fun getConclusion(
        @Header("User-Agent") userAgent: String,
        @Header("Referer") referer: String,
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("up_mid") upMid: Long?,
        @Query("wts") wts: Long,
        @Query("w_rid") wRid: String
    ): ConclusionResponse

    // =========================================================
    // 【Ver 2.5.0 新增】播放器字幕兜底方案
    // =========================================================

    /**
     * 获取播放器配置 (含字幕列表)
     * 这是一个核心高频接口，模拟播放器行为，极少被风控
     */
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/player/wbi/v2")
    suspend fun getPlayerV2(
        @Query("aid") aid: Long, // 内部使用的是 aid (即 oid)
        @Query("cid") cid: Long,
        @Query("wts") wts: Long,
        @Query("w_rid") wRid: String
    ): PlayerV2Response

    /**
     * 下载字幕 JSON 文件
     * 因为 URL 指向 B 站 CDN (subtitle.akamaized.net 等)，所以使用 @Url 动态传入
     */
    @GET
    suspend fun downloadSubtitleJson(@Url url: String): RawSubtitleJson

    // ===========================
    // 登录/退出相关接口 (保持不变)
    // ===========================

    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("https://passport.bilibili.com/x/passport-login/captcha?source=main_web")
    fun getCaptcha(): Call<BiliResponse<CaptchaResult>>

    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @FormUrlEncoded
    @POST("https://passport.bilibili.com/x/passport-login/web/sms/send")
    fun sendSmsCode(
        @Field("cid") cid: Int,
        @Field("tel") tel: Long,
        @Field("token") token: String,
        @Field("challenge") challenge: String,
        @Field("validate") validate: String,
        @Field("seccode") seccode: String,
        @Field("source") source: String = "main_web"
    ): Call<BiliResponse<SmsSendResponse>>

    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @FormUrlEncoded
    @POST("https://passport.bilibili.com/x/passport-login/web/login/sms")
    fun loginBySms(
        @Field("cid") cid: Int,
        @Field("tel") tel: Long,
        @Field("code") code: Int,
        @Field("captcha_key") captchaKey: String,
        @Field("source") source: String = "main_web"
    ): Call<BiliResponse<LoginResponseData>>

    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @FormUrlEncoded
    @POST("https://passport.bilibili.com/login/exit/v2")
    fun logout(
        @Field("biliCSRF") csrf: String
    ): Call<BiliResponse<Any>>

    // ===========================
    // 评论相关接口 (保持不变)
    // ===========================

    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @FormUrlEncoded
    @POST("x/v2/reply/add")
    suspend fun addReply(
        @Field("oid") oid: Long,
        @Field("message") message: String,
        @Field("csrf") csrf: String,
        @Field("type") type: Int = 1,
        @Field("plat") plat: Int = 1
    ): BiliResponse<ReplyResultData>

    // ===========================
    // 推荐与上报接口 (保持不变)
    // ===========================

    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/web-interface/wbi/index/top/feed/rcmd")
    suspend fun getRecommendFeed(
        @Query("wts") wts: Long,
        @Query("w_rid") wRid: String,
        @Query("ps") ps: Int = 10
    ): RecommendResponse

    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @FormUrlEncoded
    @POST("x/v2/history/report")
    suspend fun reportHistory(
        @Field("aid") aid: Long,
        @Field("cid") cid: Long,
        @Field("progress") progress: Int = 0,
        @Field("csrf") csrf: String
    ): BiliResponse<Any>
}

data class ReplyResultData(
    val rpid: Long,
    val success_toast: String?
)