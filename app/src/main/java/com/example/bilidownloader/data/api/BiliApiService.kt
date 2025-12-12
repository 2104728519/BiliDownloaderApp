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
// 【新增】UserInfo 数据模型 (通常放在 data.model 包下)
// 这里为了方便，直接放在顶部
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

    // 【新增】获取当前登录用户的信息 (使用 Nav 接口，它同时包含用户数据)
    // 注意：这里的返回类型是 UserInfoResponse，因为我们要直接处理它的 code
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/web-interface/nav")
    fun getSelfInfo(): Call<UserInfoResponse>


    // 2. 【替换】获取视频详细信息 (标题、封面、CID等)
    // 这个接口比原来的 pagelist 提供了更多的视频信息。
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
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

    // ---

    // ===========================
    // 登录/退出相关接口
    // ===========================

    // 1. 申请极验参数 (获取 gt 和 challenge)
    // 增加 User-Agent 头，防止被 B 站防火墙拦截
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
        @Field("captcha_key") captchaKey: String, // 注意这里变量名是 captchaKey
        @Field("source") source: String = "main_web"
    ): Call<BiliResponse<LoginResponseData>>

    // 4. 退出登录
    // 注意：必须带上 biliCSRF 参数，否则报错 -111
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @FormUrlEncoded
    @POST("https://passport.bilibili.com/login/exit/v2")
    fun logout(
        @Field("biliCSRF") csrf: String
    ): Call<BiliResponse<Any>> // 返回数据不重要，只要 code=0
}