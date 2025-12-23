package com.example.bilidownloader.features.login

import android.app.Application
import android.os.CountDownTimer
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bilidownloader.core.common.Resource
import com.example.bilidownloader.core.manager.CookieManager
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

class LoginViewModel(
    application: Application,
    private val authRepository: AuthRepository // 注入仓库
) : AndroidViewModel(application) {

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

    fun fetchCaptcha(phone: String) {
        if (phone.length != 11) {
            _loginState.value = LoginState.Error("请输入正确的11位手机号")
            return
        }
        currentPhone = phone.toLongOrNull() ?: 0

        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            when (val result = authRepository.fetchCaptcha()) {
                is Resource.Success -> {
                    val data = result.data!!
                    currentCaptchaToken = data.token
                    currentGeetestChallenge = data.geetest.challenge
                    _loginState.value = LoginState.CaptchaRequired(data.geetest)
                }
                is Resource.Error -> {
                    _loginState.value = LoginState.Error(result.message ?: "获取验证码失败")
                }
                else -> {}
            }
        }
    }

    fun onGeetestSuccess(validate: String, seccode: String, webViewCookie: String) {
        // 同步 WebView Cookie
        if (webViewCookie.isNotEmpty()) {
            val cookieList = webViewCookie.split(";").map { it.trim() }
            CookieManager.saveCookies(getApplication(), cookieList)
        }

        _loginState.value = LoginState.Loading

        viewModelScope.launch {
            val result = authRepository.sendSmsCode(
                tel = currentPhone,
                token = currentCaptchaToken,
                challenge = currentGeetestChallenge,
                validate = validate,
                seccode = seccode
            )

            when (result) {
                is Resource.Success -> {
                    currentSmsKey = result.data!!
                    _loginState.value = LoginState.SmsSent
                    startTimer()
                }
                is Resource.Error -> {
                    _loginState.value = LoginState.Error(result.message ?: "发送失败")
                }
                else -> {}
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

    fun login(smsCode: String) {
        if (smsCode.isEmpty()) return

        viewModelScope.launch {
            _loginState.value = LoginState.Loading
            val result = authRepository.loginBySms(
                tel = currentPhone,
                code = smsCode.toInt(),
                captchaKey = currentSmsKey
            )

            when (result) {
                is Resource.Success -> _loginState.value = LoginState.Success
                is Resource.Error -> _loginState.value = LoginState.Error(result.message ?: "登录失败")
                else -> {}
            }
        }
    }

    private fun startTimer() {
        viewModelScope.launch {
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