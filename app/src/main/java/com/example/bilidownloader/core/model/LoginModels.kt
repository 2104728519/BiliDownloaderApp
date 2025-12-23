package com.example.bilidownloader.core.model

// region 1. Captcha Info (人机验证)

data class CaptchaResult(
    val token: String,
    val geetest: GeetestInfo
)

data class GeetestInfo(
    val gt: String,        // 极验 ID
    val challenge: String  // 极验 Challenge
)

// endregion

// region 2. SMS Send (发送短信)

data class SmsSendRequest(
    val cid: Int = 86,
    val tel: Long,
    val token: String,     // Step1 获得的 token
    val challenge: String, // 极验 Challenge
    val validate: String,  // 极验验证通过后的 validate
    val seccode: String,   // 极验验证通过后的 seccode
    val source: String = "main_web"
)

data class SmsSendResponse(
    val captcha_key: String // 短信登录所需的 Key
)

// endregion

// region 3. Login (短信登录)

data class LoginRequest(
    val cid: Int = 86,
    val tel: Long,
    val code: Int,         // 短信验证码
    val captcha_key: String,
    val source: String = "main_web",
    val keep: Int = 0
)

data class LoginResponseData(
    val url: String?,
    val refresh_token: String?
)

// endregion