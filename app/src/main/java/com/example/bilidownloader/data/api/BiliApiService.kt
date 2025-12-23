package com.example.bilidownloader.data.api

import com.example.bilidownloader.data.model.*
import retrofit2.Call
import retrofit2.http.*

/**
 * 用户信息基础响应结构.
 */
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

/**
 * B 站核心 API 接口定义.
 * 涵盖了视频详情、流地址获取、WBI 密钥交换、登录流程、评论及字幕获取等全套功能.
 */
interface BiliApiService {

    // region 1. Navigation & Auth (导航与鉴权)

    /**
     * 获取导航栏用户信息.
     * 主要用途：获取 WBI 签名所需的 img_key 和 sub_key.
     */
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/web-interface/nav")
    fun getNavInfo(): Call<BiliResponse<NavData>>

    /**
     * 获取当前登录用户的个人信息.
     * 用于验证 Cookie 有效性.
     */
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/web-interface/nav")
    fun getSelfInfo(): Call<UserInfoResponse>

    // endregion

    // region 2. Video Info & Stream (视频信息与流媒体)

    /**
     * 获取视频详细信息 (View).
     * 包含标题、简介、分集 CID 等核心元数据.
     */
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/web-interface/view")
    fun getVideoView(@Query("bvid") bvid: String): Call<BiliResponse<VideoDetail>>

    /**
     * 获取视频播放地址 (PlayUrl).
     * 需传入经 WBI 签名的参数，支持 DASH 格式（含杜比、Hi-Res）.
     */
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/player/wbi/playurl")
    fun getPlayUrl(@QueryMap params: Map<String, String>): Call<BiliResponse<PlayData>>

    // endregion

    // region 3. Subtitle & AI Summary (字幕与总结)

    /**
     * 获取视频 AI 摘要和字幕 (Web 接口).
     * 注意：该接口风险较高，易触发风控.
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

    /**
     * 获取播放器配置 V2 (Plan B 字幕方案).
     * 该接口用于获取播放器内部挂载的字幕列表（CC 字幕），风控概率低，作为兜底方案.
     */
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/player/wbi/v2")
    suspend fun getPlayerV2(
        @Query("aid") aid: Long,
        @Query("cid") cid: Long,
        @Query("wts") wts: Long,
        @Query("w_rid") wRid: String
    ): PlayerV2Response

    /**
     * 下载字幕 JSON 文件.
     */
    @GET
    suspend fun downloadSubtitleJson(@Url url: String): RawSubtitleJson

    // endregion

    // region 4. Login Flow (登录流程)

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

    // endregion

    // region 5. Interaction (互动与推荐)

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

    /**
     * 上报视频观看进度.
     * 用于欺骗推荐算法，使其认为用户对该视频感兴趣.
     */
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

    // endregion
}

data class ReplyResultData(
    val rpid: Long,
    val success_toast: String?
)