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

sealed class LoginState {
    object Idle : LoginState()
    object Loading : LoginState()
    data class CaptchaRequired(val geetestInfo: GeetestInfo) : LoginState()
    object SmsSent : LoginState()
    object Success : LoginState()
    data class Error(val message: String) : LoginState()
}

class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "LoginViewModel" // 日志标签

    private val _loginState = MutableStateFlow<LoginState>(LoginState.Idle)
    val loginState = _loginState.asStateFlow()

    private val _timerText = MutableStateFlow("获取验证码")
    val timerText = _timerText.asStateFlow()
    private val _isTimerRunning = MutableStateFlow(false)
    val isTimerRunning = _isTimerRunning.asStateFlow()

    // 【修改点 1】将关键参数保存在 ViewModel 变量中，而不是依赖 UI State
    private var currentPhone: Long = 0
    private var currentCaptchaToken: String = "" // Step 1 拿到的 token
    private var currentGeetestChallenge: String = "" // Step 1 拿到的 challenge
    private var currentSmsKey: String = ""      // Step 2 发短信后拿到的 key

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
                Log.d(TAG, "Step 1 响应: code=${body?.code}, msg=${body?.message}")

                if (body?.code == 0 && body.data != null) {
                    // 保存关键参数
                    currentCaptchaToken = body.data.token
                    currentGeetestChallenge = body.data.geetest.challenge

                    Log.d(TAG, "Step 1 成功: gt=${body.data.geetest.gt}")
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

    // 【修改点 2】重写此方法，增加日志并移除对 State 的依赖
    fun onGeetestSuccess(validate: String, seccode: String) {
        Log.d(TAG, "Step 2: 极验验证成功回调。validate=$validate")

        // 立即切换状态，移除极验窗口，防止白屏
        _loginState.value = LoginState.Loading

        viewModelScope.launch(Dispatchers.IO) {
            Log.d(TAG, "Step 2: 正在请求发送短信 API...")
            try {
                val response = RetrofitClient.service.sendSmsCode(
                    cid = 86,
                    tel = currentPhone,
                    token = currentCaptchaToken,
                    challenge = currentGeetestChallenge, // 使用成员变量
                    validate = validate,
                    seccode = seccode
                ).execute()

                val body = response.body()
                Log.d(TAG, "Step 2 响应: code=${body?.code}, msg=${body?.message}")

                if (body?.code == 0 && body.data != null) {
                    Log.d(TAG, "Step 2 成功: 短信已发送，captcha_key=${body.data.captcha_key}")
                    currentSmsKey = body.data.captcha_key
                    _loginState.value = LoginState.SmsSent
                    startTimer()
                } else {
                    Log.e(TAG, "Step 2 失败: ${body?.message}")
                    _loginState.value = LoginState.Error("短信发送失败: ${body?.message}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.e(TAG, "Step 2 异常: ${e.message}")
                _loginState.value = LoginState.Error("发送短信异常: ${e.message}")
            }
        }
    }

    fun onGeetestClose() {
        Log.d(TAG, "用户关闭了极验窗口")
        // 只有当前还在显示验证码时才重置，避免打断 Loading 状态
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
                    captchaKey = currentSmsKey // 确保这里使用了正确的参数名
                ).execute()

                val body = response.body()
                Log.d(TAG, "Step 3 响应: code=${body?.code}, msg=${body?.message}")

                if (body?.code == 0) {
                    // 提取 Cookie
                    val headers = response.headers()
                    val cookies = headers.values("Set-Cookie")
                    Log.d(TAG, "Step 3 Header Cookie数量: ${cookies.size}")

                    var sessDataFound = false
                    for (cookie in cookies) {
                        if (cookie.contains("SESSDATA")) {
                            Log.d(TAG, "Step 3 找到 SESSDATA，正在保存...")
                            CookieManager.saveSessData(getApplication(), cookie)
                            sessDataFound = true
                            break
                        }
                    }

                    if (sessDataFound) {
                        _loginState.value = LoginState.Success
                    } else {
                        Log.e(TAG, "Step 3 警告: 登录接口返回成功但未找到 SESSDATA")
                        _loginState.value = LoginState.Error("登录成功但未找到凭证")
                    }
                } else {
                    _loginState.value = LoginState.Error("登录失败: ${body?.message}")
                }
            } catch (e: Exception) {
                e.printStackTrace()
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