package com.example.bilidownloader.data.model

// 1. 获取人机验证参数 (Step 1)
data class CaptchaResult(
    val token: String,
    val geetest: GeetestInfo
)

data class GeetestInfo(
    val gt: String,        // 极验 ID
    val challenge: String  // 极验 KEY
)

// 2. 发送短信验证码请求体 (Step 2)
data class SmsSendRequest(
    val cid: Int = 86,     // 国家代码，默认中国
    val tel: Long,         // 手机号
    val token: String,     // 第一步拿到的 token
    val challenge: String, // 极验 challenge
    val validate: String,  // 极验验证成功后的 validate
    val seccode: String,   // 极验验证成功后的 seccode
    val source: String = "main_web"
)

// 发送短信响应
data class SmsSendResponse(
    val captcha_key: String // 短信登录需要的 key
)

// 3. 短信登录请求体 (Step 3)
data class LoginRequest(
    val cid: Int = 86,
    val tel: Long,
    val code: Int,         // 用户收到的短信验证码
    val captcha_key: String, // 第二步拿到的 key
    val source: String = "main_web",
    val keep: Int = 0
)

// 登录响应 (Web 接口主要靠 Header 的 Set-Cookie，但在 Body 里可能也有 info)
data class LoginResponseData(
    val url: String?,
    val refresh_token: String?
)

// 用于处理 B 站通用的极验验证响应包装
// B 站很多接口成功是 code=0，但 geetest 相关接口可能不同，这里复用 BiliResponse 即可