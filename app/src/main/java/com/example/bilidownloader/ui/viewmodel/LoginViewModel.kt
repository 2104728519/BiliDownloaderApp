package com.example.bilidownloader.ui.viewmodel

import android.app.Application
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.model.GeetestInfo
import com.example.bilidownloader.core.manager.CookieManager
import com.example.bilidownloader.core.network.NetworkModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// 登录状态机
sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class CaptchaRequired(val geetestInfo: GeetestInfo) : LoginState()
    object SmsSent : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

/**
 * 登录流程 ViewModel.
 *
 * 核心难点解决：
 * **Cookie 预热 (Preheat)**：
 * B 站的风控机制要求客户端在发起短信验证码请求前，必须持有有效的 `buvid3` (设备指纹)。
 * 该 ViewModel 在初始化时会预先请求一次 Nav 接口，确保 `ReceivedCookieInterceptor`
 * 捕获并保存了 `buvid3`，从而避免“请更新 App”或 -400 错误。
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "LoginViewModel"

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState = _loginState.asStateFlow()

    private val _timerText = MutableStateFlow("获取验证码")
    val timerText = _timerText.asStateFlow()
    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning = _isTimerRunning.asStateFlow()

    private var currentPhone: Long = 0
    private var currentCaptchaToken: String = ""
    private var currentGeetestChallenge: String = ""
    private var currentSmsKey: String = ""

    init {
        preheatCookies()
    }

    /** 预热 Cookie 以获取 buvid3 */
    private fun preheatCookies() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "正在预热 Cookie (获取 buvid)...")
                NetworkModule.biliService.getNavInfo().execute()
                Log.d(TAG, "Cookie 预热完成")
            } catch (e: Exception) {
                Log.w(TAG, "Cookie 预热失败，后续短信发送可能会受阻", e)
            }
        }
    }

    /** Step 1: 请求验证码参数 (含极验 Challenge) */
    fun fetchCaptcha(phone: String) {
        if (phone.length != 11) {
            _loginState.value = LoginState.Error("请输入正确的11位手机号")
            return
        }
        currentPhone = phone.toLongOrNull() ?: 0

        viewModelScope.launch(Dispatchers.IO) {
            _loginState.value = LoginState.Loading
            try {
                val response = NetworkModule.biliService.getCaptcha().execute()
                val body = response.body()

                if (body?.code == 0 && body.data != null) {
                    currentCaptchaToken = body.data.token
                    currentGeetestChallenge = body.data.geetest.challenge

                    // 双重保险：再次检查 buvid
                    val currentCookie = CookieManager.getCookie(getApplication())
                    if (currentCookie?.contains("buvid") != true) {
                        NetworkModule.biliService.getNavInfo().execute()
                    }

                    _loginState.value = LoginState.CaptchaRequired(body.data.geetest)
                } else {
                    _loginState.value = LoginState.Error("获取验证码失败: ${body?.message}")
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("网络错误: ${e.message}")
            }
        }
    }

    /** Step 2: 极验成功，发送短信 */
    fun onGeetestSuccess(validate: String, seccode: String, webViewCookie: String) {
        Log.d(TAG, "Step 2: 极验验证成功。validate=$validate")

        // 将 WebView 生成的 Cookie (可能包含更新的指纹) 合并到本地
        if (webViewCookie.isNotEmpty()) {
            val cookieList = webViewCookie.split(";").map { it.trim() }
            CookieManager.saveCookies(getApplication(), cookieList)
        }

        _loginState.value = LoginState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val response = NetworkModule.biliService.sendSmsCode(
                    cid = 86,
                    tel = currentPhone,
                    token = currentCaptchaToken,
                    challenge = currentGeetestChallenge,
                    validate = validate,
                    seccode = seccode
                ).execute()

                val body = response.body()
                if (body?.code == 0 && body.data != null) {
                    currentSmsKey = body.data.captcha_key
                    _loginState.value = LoginState.SmsSent
                    startTimer()
                } else {
                    _loginState.value = LoginState.Error("短信发送失败: ${body?.message}")
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("发送短信异常: ${e.message}")
            }
        }
    }

    fun onGeetestClose() {
        if (_loginState.value is LoginState.CaptchaRequired) {
            _loginState.value = LoginState.Idle
        }
    }

    fun onGeetestError(msg: String) {
        _loginState.value = LoginState.Error("验证组件错误: $msg")
    }

    /** Step 3: 验证短信码并登录 */
    fun login(smsCode: String) {
        if (smsCode.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            _loginState.value = LoginState.Loading
            try {
                val response = NetworkModule.biliService.loginBySms(
                    cid = 86,
                    tel = currentPhone,
                    code = smsCode.toInt(),
                    captchaKey = currentSmsKey
                ).execute()

                val body = response.body()
                if (body?.code == 0) {
                    // 从 Response Header 提取关键的 Set-Cookie
                    val cookies = response.headers().values("Set-Cookie")
                    if (cookies.isNotEmpty()) {
                        CookieManager.saveCookies(getApplication(), cookies)
                        _loginState.value = LoginState.Success
                    } else {
                        _loginState.value = LoginState.Error("登录成功但未找到凭证")
                    }
                } else {
                    _loginState.value = LoginState.Error("登录失败: ${body?.message}")
                }
            } catch (e: Exception) {
                _loginState.value = LoginState.Error("登录异常: ${e.message}")
            }
        }
    }

    private fun startTimer() {
        viewModelScope.launch(Dispatchers.Main) {
            _isTimerRunning.value = true
            object : CountDownTimer(60000, 1000) {
                override fun onTick(millisUntilFinished: Long) {
                    _timerText.value = "${millisUntilFinished / 1000}s"
                }
                override fun onFinish() {
                    _timerText.value = "获取验证码"
                    _isTimerRunning.value = false
                }
            }.start()
        }
    }
}