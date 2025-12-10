package com.example.bilidownloader.ui.viewmodel

import android.app.Application
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.data.api.RetrofitClient
import com.example.bilidownloader.data.model.GeetestInfo
import com.example.bilidownloader.utils.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ... (LoginState 定义保持不变)
sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class CaptchaRequired(val geetestInfo: GeetestInfo) : LoginState()
    object SmsSent : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

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

    // 【新增】初始化时预先请求一次 B 站接口，获取指纹 Cookie (buvid3)
    // 这一步对于绕过“请下载最新App”的错误至关重要
    init {
        preheatCookies()
    }

    private fun preheatCookies() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "正在预热 Cookie (获取 buvid)...")
                // getNavInfo 会返回 buvid3 和 b_nut，RetrofitClient 会自动保存
                RetrofitClient.service.getNavInfo().execute()
                Log.d(TAG, "Cookie 预热完成")
            } catch (e: Exception) {
                Log.w(TAG, "Cookie 预热失败，后续短信发送可能会受阻", e)
            }
        }
    }

    fun fetchCaptcha(phone: String) {
        if (phone.length != 11) {
            _loginState.value = LoginState.Error("请输入正确的11位手机号")
            return
        }
        currentPhone = phone.toLongOrNull() ?: 0

        viewModelScope.launch(Dispatchers.IO) {
            _loginState.value = LoginState.Loading
            Log.d(TAG, "Step 1: 开始获取验证码参数...")

            try {
                val response = RetrofitClient.service.getCaptcha().execute()
                val body = response.body()

                if (body?.code == 0 && body.data != null) {
                    currentCaptchaToken = body.data.token
                    currentGeetestChallenge = body.data.geetest.challenge

                    // 检查一下是否有 buvid，如果没有再尝试获取一次（双重保险）
                    val currentCookie = CookieManager.getCookie(getApplication())
                    if (currentCookie?.contains("buvid") != true) {
                        Log.w(TAG, "警告：请求验证码时仍未发现 buvid，尝试紧急补救...")
                        RetrofitClient.service.getNavInfo().execute() // 再次尝试获取
                    }

                    _loginState.value = LoginState.CaptchaRequired(body.data.geetest)
                } else {
                    _loginState.value = LoginState.Error("获取验证码失败: ${body?.message}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _loginState.value = LoginState.Error("网络错误(Step1): ${e.message}")
            }
        }
    }

    // 【修改】接收 webViewCookie 参数，并执行同步和检查
    fun onGeetestSuccess(validate: String, seccode: String, webViewCookie: String) {
        Log.d(TAG, "Step 2: 极验验证成功。validate=$validate")

        // 【核心操作】将 WebView 的 Cookie 同步到我们的 CookieManager
        if (webViewCookie.isNotEmpty()) {
            // WebView 的 Cookie 格式是 "key=value; key2=value2"
            // 我们将其分割成列表保存
            val cookieList = webViewCookie.split(";").map { it.trim() }
            CookieManager.saveCookies(getApplication(), cookieList)
            Log.d(TAG, "【检查点 A】WebView Cookie 已同步: $webViewCookie")
        } else {
            Log.w(TAG, "【检查点 A 警告】WebView 未返回 Cookie！")
        }

        _loginState.value = LoginState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            // 【检查点 B】打印发送请求前，Retrofit 即将使用的 Cookie
            val currentStoredCookie = CookieManager.getCookie(getApplication())
            Log.d(TAG, "【检查点 B】发送短信前的最终 Cookie: $currentStoredCookie")

            // 检查 buvid3 是否存在
            if (currentStoredCookie?.contains("buvid3") != true) {
                Log.e(TAG, "【致命错误】Cookie 中缺失 buvid3，请求必定失败！")
            }

            Log.d(TAG, "Step 2: 正在请求发送短信 API...")
            try {
                val response = RetrofitClient.service.sendSmsCode(
                    cid = 86,
                    tel = currentPhone,
                    token = currentCaptchaToken,
                    challenge = currentGeetestChallenge,
                    validate = validate,
                    seccode = seccode
                ).execute()

                val body = response.body()
                Log.d(TAG, "Step 2 响应: code=${body?.code}, msg=${body?.message}")

                if (body?.code == 0 && body.data != null) {
                    currentSmsKey = body.data.captcha_key
                    _loginState.value = LoginState.SmsSent
                    startTimer()
                } else {
                    Log.e(TAG, "Step 2 失败: ${body?.message}")
                    _loginState.value = LoginState.Error("短信发送失败: ${body?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Step 2 异常: ${e.message}")
                _loginState.value = LoginState.Error("发送短信异常: ${e.message}")
            }
        }
    }

    fun onGeetestClose() {
        Log.d(TAG, "用户关闭了极验窗口")
        if (_loginState.value is LoginState.CaptchaRequired) {
            _loginState.value = LoginState.Idle
        }
    }

    fun onGeetestError(msg: String) {
        Log.e(TAG, "极验组件报错: $msg")
        _loginState.value = LoginState.Error("验证组件错误: $msg")
    }

    fun login(smsCode: String) {
        if (smsCode.isEmpty()) return
        Log.d(TAG, "Step 3: 开始尝试登录，验证码: $smsCode")

        viewModelScope.launch(Dispatchers.IO) {
            _loginState.value = LoginState.Loading
            try {
                val response = RetrofitClient.service.loginBySms(
                    cid = 86,
                    tel = currentPhone,
                    code = smsCode.toInt(),
                    captchaKey = currentSmsKey
                ).execute()

                val body = response.body()
                Log.d(TAG, "Step 3 响应: code=${body?.code}, msg=${body?.message}")

                if (body?.code == 0) {
                    val headers = response.headers()
                    val cookies = headers.values("Set-Cookie")

                    if (cookies.isNotEmpty()) {
                        // 使用新的保存方法
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