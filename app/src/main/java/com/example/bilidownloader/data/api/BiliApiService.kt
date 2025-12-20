package com.example.bilidownloader.data.api

import com.example.bilidownloader.data.model.*
import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.QueryMap
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.POST

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
     * 获取视频 AI 摘要和字幕
     */
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/web-interface/view/conclusion/get")
    suspend fun getConclusion(
        @Query("bvid") bvid: String,
        @Query("cid") cid: Long,
        @Query("up_mid") upMid: Long?,
        @Query("wts") wts: Long,
        @Query("w_rid") wRid: String
    ): ConclusionResponse

    // ===========================
    // 登录/退出相关接口
    // ===========================

    // 1. 申请极验参数
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("https://passport.bilibili.com/x/passport-login/captcha?source=main_web")
    fun getCaptcha(): Call<BiliResponse<CaptchaResult>>

    // 2. 发送短信验证码
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

    // 3. 验证短信码并登录
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

    // 4. 退出登录
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
    // 【Ver 2.3.0 新增】评论相关接口
    // ===========================

    /**
     * 发送评论
     */
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @FormUrlEncoded
    @POST("x/v2/reply/add")
    suspend fun addReply(
        @Field("oid") oid: Long,           // 视频 aid
        @Field("message") message: String, // 评论内容
        @Field("csrf") csrf: String,       // bili_jct
        @Field("type") type: Int = 1,      // 1=视频
        @Field("plat") plat: Int = 1       // 1=Web
    ): BiliResponse<ReplyResultData>
}

// 对应评论接口返回的 data 字段
data class ReplyResultData(
    val rpid: Long,
    val success_toast: String?
)