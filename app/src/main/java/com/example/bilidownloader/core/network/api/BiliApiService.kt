package com.example.bilidownloader.core.network.api

import com.example.bilidownloader.core.model.BiliResponse
import com.example.bilidownloader.features.login.CaptchaResult
import com.example.bilidownloader.core.model.ConclusionResponse
import com.example.bilidownloader.features.login.LoginResponseData
import com.example.bilidownloader.core.model.NavData
import com.example.bilidownloader.core.model.PlayData
import com.example.bilidownloader.core.model.PlayerV2Response
import com.example.bilidownloader.core.model.RawSubtitleJson
import com.example.bilidownloader.features.login.SmsSendResponse
import com.example.bilidownloader.core.model.VideoDetail
import com.example.bilidownloader.core.model.BiliHistoryResponse
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
 */
interface BiliApiService {

    // region 1. Navigation & Auth (导航与鉴权)

    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/web-interface/nav")
    fun getNavInfo(): Call<BiliResponse<NavData>>

    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/web-interface/nav")
    fun getSelfInfo(): Call<UserInfoResponse>

    // endregion

    // region 2. Video Info & Stream (视频信息与流媒体)

    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/web-interface/view")
    fun getVideoView(@Query("bvid") bvid: String): Call<BiliResponse<VideoDetail>>

    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36",
        "Referer: https://www.bilibili.com/"
    )
    @GET("x/player/wbi/playurl")
    fun getPlayUrl(@QueryMap params: Map<String, String>): Call<BiliResponse<PlayData>>

    // endregion

    // region 3. Subtitle & AI Summary (字幕与总结)

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

    // region 5. Interaction (互动与历史)

    /**
     * 上报视频观看进度.
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

    /**
     * 获取账号云端历史记录.
     */
    @Headers(
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
        "Referer: https://www.bilibili.com/account/history"
    )
    @GET("x/web-interface/history/cursor")
    suspend fun getHistory(
        @Query("view_at") viewAt: Long = 0,
        @Query("max") max: Long = 0,
        @Query("ps") ps: Int = 20,
        @Query("business") business: String = "archive"
    ): BiliHistoryResponse

    // endregion
}